package com.harshalbhatia.whatsopen

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.harshalbhatia.whatsopen.databinding.FragmentCallLogsBinding
import kotlinx.coroutines.launch

/**
 * Lists recent calls so the user can open a WhatsApp chat with a caller.
 *
 * The call log permission is only asked for when the user taps Allow access. Names come from
 * the call log's own cache, so contacts aren't read. Nothing read is stored.
 */
class CallLogsFragment : Fragment(), CallLogsAdapter.Callbacks {
    private var _binding: FragmentCallLogsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CallLogsViewModel by viewModels()
    private var adapter: CallLogsAdapter? = null

    /** The open ⋮ menu of a row, if any. */
    @VisibleForTesting
    internal var rowMenu: PopupMenu? = null
        private set

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CallLogsAdapter(this).also { adapter = it }
        binding.recyclerView.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { adapter.submit(CallLogsEntry.forState(it)) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter?.refreshTimes()
        // The permission may have been granted or revoked in Settings meanwhile.
        refresh()
    }

    /** Works out what the tab can show, and reads the call log if it may. */
    private fun refresh() {
        val context = requireContext()
        when {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) -> viewModel.showNoTelephony()
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.load()
            // Android shows no rationale before the first request, nor after "don't ask again";
            // only the second means a request would be refused without asking.
            else -> viewModel.showNeedsPermission(
                permanentlyDenied = permissionRequested() &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)
            )
        }
    }

    override fun onFilterChanged(filter: CallLogFilter) = viewModel.setFilter(filter)

    override fun onMessageAction(message: CallLogsMessage) {
        when (message) {
            CallLogsMessage.PERMISSION -> {
                prefs().edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
                requestPermission.launch(Manifest.permission.READ_CALL_LOG)
            }
            CallLogsMessage.PERMISSION_BLOCKED -> startOrComplain(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().packageName, null))
            )
            CallLogsMessage.ERROR -> refresh()
            CallLogsMessage.NO_MATCHES -> viewModel.setFilter(CallLogFilter())
            else -> Unit
        }
    }

    override fun onCallClick(item: CallLogItem) {
        WhatsAppLauncher.openChat(requireContext(), item.number, onError = { showSnackbar(it) })
    }

    override fun onMoreOptions(item: CallLogItem, anchor: View) {
        rowMenu?.dismiss()
        rowMenu = PopupMenu(requireContext(), anchor, Gravity.END).apply {
            inflate(R.menu.call_log_row)
            menu.findItem(R.id.action_save_contact).isVisible = !item.isContact
            menu.findItem(R.id.action_view_contact).isVisible = item.isContact
            setOnMenuItemClickListener { onMenuItem(item, it.itemId) }
            setOnDismissListener { if (rowMenu === it) rowMenu = null }
            show()
        }
    }

    private fun onMenuItem(item: CallLogItem, id: Int): Boolean {
        when (id) {
            R.id.action_call -> dial(item.number)
            R.id.action_copy_number -> copyNumber(item.number)
            R.id.action_save_contact -> startOrComplain(
                Intent(Intent.ACTION_INSERT_OR_EDIT)
                    .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    .putExtra(ContactsContract.Intents.Insert.PHONE, item.number)
            )
            R.id.action_view_contact -> startOrComplain(viewContactIntent(item))
            else -> return false
        }
        return true
    }

    /**
     * Shows the contact the call log cached for [item]. Other apps can write to the call log,
     * so its lookup URI is only opened if it points into the contacts provider; otherwise the
     * contacts app looks the number up.
     */
    private fun viewContactIntent(item: CallLogItem): Intent {
        val lookupUri = item.lookupUri?.let(Uri::parse)
        return if (lookupUri?.scheme == ContentResolver.SCHEME_CONTENT && lookupUri.authority == ContactsContract.AUTHORITY) {
            Intent(Intent.ACTION_VIEW, lookupUri)
        } else {
            Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, telUri(item.number))
        }
    }

    /**
     * Opens the phone app with [number] ready to call. This app handles tel: links too, so the
     * default dialer is asked first; a plain ACTION_DIAL could offer this app back to the user.
     */
    private fun dial(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, telUri(number))
        val dialer = requireContext().getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        if (dialer == null || dialer == requireContext().packageName || !start(Intent(intent).setPackage(dialer))) {
            startOrComplain(intent)
        }
    }

    private fun copyNumber(e164: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.phone_number_clip_label), e164))
        // Android 13+ confirms copies itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) showSnackbar(R.string.number_copied)
    }

    /** Starts [intent], or returns false if no app can handle it. */
    private fun start(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        // The only handler isn't exported, or needs a permission this app doesn't have.
        false
    }

    private fun startOrComplain(intent: Intent) {
        if (!start(intent)) showSnackbar(R.string.error_no_app_for_action)
    }

    /** Whether the user has been asked for the call log permission. No call data is kept. */
    private fun permissionRequested() = prefs().getBoolean(KEY_PERMISSION_REQUESTED, false)

    private fun prefs() = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onDestroyView() {
        rowMenu?.dismiss()
        rowMenu = null
        adapter = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val PREFS_NAME = "call_logs"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"

        fun telUri(number: String): Uri = Uri.fromParts("tel", number, null)
    }
}
