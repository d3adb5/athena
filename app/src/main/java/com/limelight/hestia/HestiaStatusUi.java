package com.limelight.hestia;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;

import java.io.IOException;

/**
 * Turns a {@link HestiaHostStatus} into icons, colours and localised text,
 * and hosts the shared "Hermes status" dialog used by the host list and the
 * app list. All protocol interpretation lives in HestiaHostStatus.
 */
public final class HestiaStatusUi {
    private HestiaStatusUi() {}

    /** Indicators are shown only for online, paired hosts that answered as Hermes. */
    public static boolean shouldShow(ComputerDetails computer) {
        return computer != null && computer.state == ComputerDetails.State.ONLINE
                && computer.pairState == PairingManager.PairState.PAIRED
                && computer.isKnownHermes();
    }

    public static int iconFor(HestiaHostStatus.Level level) {
        switch (level) {
            case ERROR:
                return R.drawable.ic_hestia_error;
            case WARNING:
                return R.drawable.ic_hestia_warning;
            default:
                return R.drawable.ic_hestia_ok;
        }
    }

    public static int colorFor(Context context, HestiaHostStatus.Level level) {
        int res;
        switch (level) {
            case ERROR:
                res = R.color.hestia_error;
                break;
            case WARNING:
                res = R.color.hestia_warning;
                break;
            case OK:
                res = R.color.hestia_ok;
                break;
            default:
                res = R.color.hestia_info;
                break;
        }
        return ContextCompat.getColor(context, res);
    }

    /** Short label for the host grid, e.g. "Hermes · Ready". */
    public static String badgeLabel(Context context, HestiaHostStatus status) {
        switch (status.getLevel()) {
            case ERROR:
                return context.getString(R.string.hestia_badge_error);
            case WARNING:
                return context.getString(R.string.hestia_badge_warning);
            default:
                return context.getString(R.string.hestia_badge_ok);
        }
    }

    /** One line for the app-list banner: the most important finding. */
    public static String headline(Context context, HestiaHostStatus status) {
        HestiaHostStatus.Item item = status.getHeadline();
        String text = item != null ? describe(context, item, status.isDisplayRecoveryOffered())
                : context.getString(R.string.hestia_item_ready);
        return context.getString(R.string.hestia_banner_format, versionLabel(status), text);
    }

    public static String formatFps(int[] fps) {
        StringBuilder sb = new StringBuilder();
        for (int value : fps) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private static String versionLabel(HestiaHostStatus status) {
        return status.getServerVersion().isEmpty() ? "Hermes" : "Hermes " + status.getServerVersion();
    }

    /** Localised explanation of one finding. */
    public static String describe(Context c, HestiaHostStatus.Item item, boolean recoveryOffered) {
        switch (item.code) {
            case READY:
                return c.getString(R.string.hestia_item_ready);
            case PROTOCOL_INCOMPATIBLE:
                return c.getString(R.string.hestia_item_protocol_incompatible,
                        HestiaCapabilities.CLIENT_PROTOCOL, item.detail);
            case API_UNREACHABLE:
                return c.getString(R.string.hestia_item_api_unreachable);
            case API_ERROR:
                return c.getString(R.string.hestia_item_api_error, orNoResponse(c, item.detail));
            case CERTIFICATE_REJECTED:
                return c.getString(R.string.hestia_item_certificate_rejected);
            case NO_STREAM_PERMISSION:
                return c.getString(R.string.hestia_item_no_stream_permission);
            case NO_LAUNCH_PERMISSION:
                return c.getString(R.string.hestia_item_no_launch_permission);
            case NO_LIST_PERMISSION:
                return c.getString(R.string.hestia_item_no_list_permission);
            case NO_INPUT_PERMISSION:
                return c.getString(R.string.hestia_item_no_input_permission, inputNames(c, item.detail));
            case NO_VIEW_PERMISSION:
                return c.getString(R.string.hestia_item_no_view_permission);
            case DIAGNOSTICS_UNAVAILABLE:
                return c.getString(R.string.hestia_item_diagnostics_unavailable,
                        "display".equals(item.checkId)
                                ? c.getString(R.string.hestia_endpoint_display)
                                : c.getString(R.string.hestia_endpoint_diagnostics),
                        orNoResponse(c, item.detail));
            case PREFLIGHT_CHECK:
                return c.getString(R.string.hestia_item_check, checkLabel(c, item.checkId),
                        item.detail.isEmpty() ? c.getString(R.string.hestia_check_not_ready) : item.detail);
            case DISPLAY_LEFT_DISABLED:
                return c.getString(recoveryOffered ? R.string.hestia_item_display_left_disabled_recoverable
                        : R.string.hestia_item_display_left_disabled);
            case LAST_SESSION_ENDED:
                return c.getString(R.string.hestia_item_last_session_ended, terminationReason(c, item.detail));
            case STREAMING: {
                int sessions = 1;
                try {
                    sessions = Integer.parseInt(item.detail);
                } catch (NumberFormatException ignored) {
                }
                return c.getResources().getQuantityString(R.plurals.hestia_item_streaming, sessions, sessions);
            }
            case AWAITING_RECONNECT:
                return c.getString(R.string.hestia_item_awaiting_reconnect);
            case VIRTUAL_DISPLAY_ACTIVE:
                return c.getString(R.string.hestia_item_virtual_display_active, item.detail);
            case CLIPBOARD_UNAVAILABLE:
                return c.getString(R.string.hestia_item_clipboard_unavailable, clipboardHint(c, item.detail));
            default:
                return item.toString();
        }
    }

    private static String orNoResponse(Context c, String detail) {
        return detail == null || detail.isEmpty() ? c.getString(R.string.hestia_detail_no_response) : detail;
    }

    private static String inputNames(Context c, String detail) {
        StringBuilder sb = new StringBuilder();
        for (String input : detail.split(",")) {
            String name;
            switch (input) {
                case "keyboard":
                    name = c.getString(R.string.hestia_input_keyboard);
                    break;
                case "mouse":
                    name = c.getString(R.string.hestia_input_mouse);
                    break;
                case "gamepad":
                    name = c.getString(R.string.hestia_input_gamepad);
                    break;
                default:
                    name = input;
                    break;
            }
            if (name.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    private static String checkLabel(Context c, String id) {
        switch (id) {
            case "encoder":
                return c.getString(R.string.hestia_check_encoder);
            case "display":
                return c.getString(R.string.hestia_check_display);
            case "virtual_display":
                return c.getString(R.string.hestia_check_virtual_display);
            case "session_environment":
                return c.getString(R.string.hestia_check_session_environment);
            case "preflight":
                return c.getString(R.string.hestia_check_preflight);
            default:
                return id;
        }
    }

    private static String terminationReason(Context c, String reason) {
        switch (reason) {
            case "handshake_failed":
                return c.getString(R.string.hestia_termination_handshake_failed);
            case "protocol_error":
                return c.getString(R.string.hestia_termination_protocol_error);
            case "permission_revoked":
                return c.getString(R.string.hestia_termination_permission_revoked);
            case "client_lost":
                return c.getString(R.string.hestia_termination_client_lost);
            default:
                return reason;
        }
    }

    private static String clipboardHint(Context c, String diagnostic) {
        switch (diagnostic) {
            case "wl_clipboard_missing":
                return c.getString(R.string.hestia_clipboard_wl_missing);
            case "xclip_missing":
                return c.getString(R.string.hestia_clipboard_xclip_missing);
            case "desktop_session_unavailable":
                return c.getString(R.string.hestia_clipboard_no_session);
            default:
                return diagnostic.isEmpty() ? c.getString(R.string.hestia_detail_no_response) : diagnostic;
        }
    }

    private static String glyphFor(HestiaHostStatus.Level level) {
        switch (level) {
            case ERROR:
                return "✖";   // heavy multiplication x
            case WARNING:
                return "⚠";   // warning sign
            case OK:
                return "✔";   // heavy check mark
            default:
                return "•";   // bullet
        }
    }

    /** Full multi-line report: one coloured line per finding, then version and age. */
    public static CharSequence buildReport(Context context, HestiaHostStatus status) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (HestiaHostStatus.Item item : status.getItems()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            int start = sb.length();
            sb.append(glyphFor(item.level)).append("  ");
            sb.setSpan(new ForegroundColorSpan(colorFor(context, item.level)),
                    start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int textStart = sb.length();
            sb.append(describe(context, item, status.isDisplayRecoveryOffered()));
            if (item.code == HestiaHostStatus.Code.READY) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), textStart, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        sb.append("\n\n");
        int footerStart = sb.length();
        sb.append(context.getString(R.string.hestia_dialog_footer,
                versionLabel(status), HestiaCapabilities.CLIENT_PROTOCOL,
                DateUtils.getRelativeTimeSpanString(status.getCheckedAtMs(),
                        System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS)));
        sb.setSpan(new ForegroundColorSpan(colorFor(context, HestiaHostStatus.Level.INFO)),
                footerStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /**
     * Show the cached status. "Refresh" probes the host again; "Restore
     * display" appears only when the physical monitor was left off and this
     * client may recover it.
     */
    public static void showStatusDialog(final Activity activity, final ComputerDetails computer,
            final String uniqueId, final Runnable onStatusChanged) {
        final HestiaHostStatus status = computer.hestiaStatus;
        if (activity.isFinishing() || status == null || !status.isHermes()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setIcon(iconFor(status.getLevel()))
                .setTitle(activity.getString(R.string.hestia_dialog_title, computer.name))
                .setMessage(buildReport(activity, status))
                .setPositiveButton(R.string.hestia_dialog_close, null)
                .setNeutralButton(R.string.hestia_dialog_refresh,
                        (dialog, which) -> refreshAndShow(activity, computer, uniqueId, onStatusChanged));
        if (status.isDisplayRecoveryOffered()) {
            builder.setNegativeButton(R.string.hestia_dialog_restore_display,
                    (dialog, which) -> confirmDisplayRecovery(activity, computer, uniqueId, onStatusChanged));
        }
        builder.show();
    }

    /** Probe the host now (off the UI thread), then show the dialog with fresh findings. */
    public static void refreshAndShow(final Activity activity, final ComputerDetails computer,
            final String uniqueId, final Runnable onStatusChanged) {
        final SpinnerDialog spinner = SpinnerDialog.displayDialog(activity,
                activity.getString(R.string.hestia_dialog_title, computer.name),
                activity.getString(R.string.hestia_checking), false);
        new Thread(() -> {
            HestiaHostStatus fresh = null;
            try {
                fresh = createHttp(activity, computer, uniqueId).getHestiaHostStatus(computer.isKnownHermes());
            } catch (IOException e) {
                LimeLog.warning("Hestia status refresh failed: " + e.getMessage());
            }
            final HestiaHostStatus result = fresh;
            activity.runOnUiThread(() -> {
                spinner.dismiss();
                if (activity.isFinishing()) {
                    return;
                }
                if (result == null) {
                    Toast.makeText(activity, R.string.hestia_refresh_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                if (!result.equals(computer.hestiaStatus)) {
                    computer.hestiaStatus = result;
                    if (onStatusChanged != null) {
                        onStatusChanged.run();
                    }
                } else {
                    computer.hestiaStatus = result;
                }
                if (result.isHermes()) {
                    showStatusDialog(activity, computer, uniqueId, onStatusChanged);
                } else {
                    Toast.makeText(activity, R.string.hestia_no_longer_hermes, Toast.LENGTH_LONG).show();
                }
            });
        }, "HestiaStatusRefresh").start();
    }

    private static void confirmDisplayRecovery(final Activity activity, final ComputerDetails computer,
            final String uniqueId, final Runnable onStatusChanged) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.hestia_dialog_restore_display)
                .setMessage(R.string.hestia_restore_display_confirm)
                .setPositiveButton(R.string.hestia_dialog_restore_display,
                        (dialog, which) -> recoverDisplay(activity, computer, uniqueId, onStatusChanged))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void recoverDisplay(final Activity activity, final ComputerDetails computer,
            final String uniqueId, final Runnable onStatusChanged) {
        final SpinnerDialog spinner = SpinnerDialog.displayDialog(activity,
                activity.getString(R.string.hestia_dialog_restore_display),
                activity.getString(R.string.hestia_restoring_display), false);
        new Thread(() -> {
            String error = null;
            try {
                HestiaApiResponse response = createHttp(activity, computer, uniqueId).recoverHestiaDisplay();
                if (!response.isOk()) {
                    error = response.describeError();
                }
            } catch (IOException e) {
                error = activity.getString(R.string.hestia_detail_no_response);
            }
            final String finalError = error;
            activity.runOnUiThread(() -> {
                spinner.dismiss();
                if (activity.isFinishing()) {
                    return;
                }
                if (finalError == null) {
                    Toast.makeText(activity, R.string.hestia_restore_display_ok, Toast.LENGTH_LONG).show();
                    // Show the effect right away instead of waiting for the next poll.
                    refreshAndShow(activity, computer, uniqueId, onStatusChanged);
                } else {
                    Toast.makeText(activity, activity.getString(R.string.hestia_restore_display_failed,
                            finalError), Toast.LENGTH_LONG).show();
                }
            });
        }, "HestiaDisplayRecover").start();
    }

    private static NvHTTP createHttp(Context context, ComputerDetails computer, String uniqueId)
            throws IOException {
        return new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                uniqueId, computer.serverCert, PlatformBinding.getCryptoProvider(context));
    }
}
