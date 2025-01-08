package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProConController extends AbstractController {

    private static final int PACKET_SIZE = 64;
    private static final byte[] RUMBLE_NEUTRAL = {0x00, 0x01, 0x40, 0x40};
    private static final byte[] RUMBLE = {0x74, (byte) 0xBE, (byte) 0xBD, 0x6F};
    private static final int CALIBRATION_OFFSET = 0x603D;
    private static final int CALIBRATION_LENGTH = 0x12;

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private UsbEndpoint inEndpt, outEndpt;
    private Thread inputThread;
    private boolean stopped = false;
    private final int[][][] stickCalibration = new int[2][2][3]; // [stick][axis][min, center, max]
    private final float[][][] stickExtends = new float[2][2][2]; // Pre-calculated scale for each axis

    public static boolean canClaimDevice(UsbDevice device) {
        return (device.getVendorId() == 0x057e && device.getProductId() == 0x2009);
    }

    public ProConController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_NINTENDO;
        this.capabilities = MoonBridge.LI_CCAP_GYRO | MoonBridge.LI_CCAP_ACCEL | MoonBridge.LI_CCAP_RUMBLE;
    }

    private Thread createInputThread() {
        return new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            notifyDeviceAdded();

            loadStickCalibration();
            enableIMU(true);
            enableVibration(true);

            while (!Thread.currentThread().isInterrupted() && !stopped) {
                byte[] buffer = new byte[64];
                int res;
                do {
                    long lastMillis = SystemClock.uptimeMillis();
                    res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 3000);
                    if (res == 0) {
                        res = -1;
                    }
                    if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                        LimeLog.warning("Detected device I/O error");
                        ProConController.this.stop();
                        break;
                    }
                } while (res == -1 && !Thread.currentThread().isInterrupted() && !stopped);

                if (res == -1 || stopped) {
                    break;
                }

                if (handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))) {
                    reportInput();
                    reportMotion();
                }
            }
        });
    }

    public boolean start() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim interfaces");
                return false;
            }
        }

        UsbInterface iface = device.getInterface(0);
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                inEndpt = endpt;
            } else if (endpt.getDirection() == UsbConstants.USB_DIR_OUT) {
                outEndpt = endpt;
            }
        }

        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("Missing required endpoint");
            return false;
        }

        inputThread = createInputThread();
        inputThread.start();

        return true;
    }

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        rumble((short) 0, (short) 0);
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            connection.releaseInterface(iface);
        }
        connection.close();
        notifyDeviceRemoved();
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        byte[] data = {
                0x10, 0x00, RUMBLE[0], RUMBLE[1], RUMBLE[2], RUMBLE[3],
                RUMBLE[0], RUMBLE[1], RUMBLE[2], RUMBLE[3]
        };
        connection.bulkTransfer(outEndpt, data, data.length, 100);
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // ProCon does not support trigger-specific rumble
    }

    protected boolean handleRead(ByteBuffer buffer) {
        if (buffer.remaining() < PACKET_SIZE) {
            return false;
        }

        buttonFlags = 0;
        // Nintendo layout is swapped
        setButtonFlag(ControllerPacket.B_FLAG, buffer.get(3) & 0x08);
        setButtonFlag(ControllerPacket.A_FLAG, buffer.get(3) & 0x04);
        setButtonFlag(ControllerPacket.Y_FLAG, buffer.get(3) & 0x02);
        setButtonFlag(ControllerPacket.X_FLAG, buffer.get(3) & 0x01);
        setButtonFlag(ControllerPacket.UP_FLAG, buffer.get(5) & 0x02);
        setButtonFlag(ControllerPacket.DOWN_FLAG, buffer.get(5) & 0x01);
        setButtonFlag(ControllerPacket.LEFT_FLAG, buffer.get(5) & 0x08);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, buffer.get(5) & 0x04);
        setButtonFlag(ControllerPacket.BACK_FLAG, buffer.get(4) & 0x01);
        setButtonFlag(ControllerPacket.PLAY_FLAG, buffer.get(4) & 0x02);
        setButtonFlag(ControllerPacket.MISC_FLAG, buffer.get(4) & 0x20); // Screenshot
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buffer.get(4) & 0x10); // Home
        setButtonFlag(ControllerPacket.LB_FLAG, buffer.get(5) & 0x40);
        setButtonFlag(ControllerPacket.RB_FLAG, buffer.get(3) & 0x40);
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, buffer.get(4) & 0x08);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, buffer.get(4) & 0x04);

        leftTrigger = ((buffer.get(5) & 0x80) != 0) ? 1 : 0;
        rightTrigger = ((buffer.get(3) & 0x80) != 0) ? 1 : 0;

        int _leftStickX = buffer.get(6) & 0xFF | ((buffer.get(7) & 0x0F) << 8);
        int _leftStickY = ((buffer.get(7) & 0xF0) >> 4) | (buffer.get(8) << 4);
        int _rightStickX = buffer.get(9) & 0xFF | ((buffer.get(10) & 0x0F) << 8);
        int _rightStickY = ((buffer.get(10) & 0xF0) >> 4) | (buffer.get(11) << 4);

        leftStickX = applyStickCalibration(_leftStickX, 0, 0);
        leftStickY = applyStickCalibration(-_leftStickY - 1, 0, 1);
        rightStickX = applyStickCalibration(_rightStickX, 1, 0);
        rightStickY = applyStickCalibration(-_rightStickY - 1, 1, 1);

        accelX = buffer.getShort(37) / 4096.0f;
        accelY = buffer.getShort(39) / 4096.0f;
        accelZ = buffer.getShort(41) / 4096.0f;
        gyroZ = -buffer.getShort(43) / 16.0f;
        gyroX = -buffer.getShort(45) / 16.0f;
        gyroY = buffer.getShort(47) / 16.0f;

        return true;
    }

    private boolean spiFlashRead(int offset, int length, byte[] buffer) {
        byte[] command = new byte[11];
        command[0] = 0x01;  // Rumble subcommand
        command[1] = 0x00;  // Subcommand counter (can increment)
        System.arraycopy(RUMBLE_NEUTRAL, 0, command, 2, RUMBLE_NEUTRAL.length);
        System.arraycopy(RUMBLE_NEUTRAL, 0, command, 6, RUMBLE_NEUTRAL.length);
        command[10] = 0x10;  // SPI Flash Read Subcommand

        // SPI Read Address (Little Endian)
        byte[] address = {
                (byte) (offset & 0xFF),
                (byte) ((offset >> 8) & 0xFF),
                (byte) ((offset >> 16) & 0xFF),
                (byte) ((offset >> 24) & 0xFF),
                (byte) length
        };

        // Append address to command
        byte[] fullCommand = new byte[command.length + address.length];
        System.arraycopy(command, 0, fullCommand, 0, command.length);
        System.arraycopy(address, 0, fullCommand, command.length, address.length);

        if (!sendSubcommand((byte) 0x10, fullCommand)) {
            LimeLog.warning("SPI Flash Read subcommand failed.");
            return false;
        }

        // Wait for response
        int res = connection.bulkTransfer(inEndpt, buffer, PACKET_SIZE, 1000);
        if (res <= 0 || buffer[0] != 0x21 || buffer[14] != 0x10) {
            LimeLog.warning("Failed to receive SPI Flash data.");
            return false;
        }

        return true;
    }

    private void loadStickCalibration() {
        byte[] buffer = new byte[PACKET_SIZE];

        // Read calibration data from SPI flash
        if (!spiFlashRead(CALIBRATION_OFFSET, CALIBRATION_LENGTH, buffer)) {
            LimeLog.warning("Failed to read stick calibration from SPI flash. Applying default calibration.");
            applyDefaultCalibration();
            return;
        }

        // Parse calibration data
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                stickCalibration[i][j][0] = ((buffer[20 + i * 6 + j * 3] & 0xFF) | ((buffer[21 + i * 6 + j * 3] & 0x0F) << 8)) & 0xFFF;
                stickCalibration[i][j][1] = ((buffer[22 + i * 6] & 0xFF) << 4) | ((buffer[21 + i * 6 + j * 3] & 0xF0) >> 4);
                stickCalibration[i][j][2] = ((buffer[23 + i * 6 + j * 3] & 0xFF) | ((buffer[24 + i * 6 + j * 3] & 0x0F) << 8)) & 0xFFF;

                stickExtends[i][j][0] = (float) (stickCalibration[i][j][0] * 0.7);
                stickExtends[i][j][1] = (float) (stickCalibration[i][j][2] * 0.7);
            }
        }
    }

    private void applyDefaultCalibration() {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                stickCalibration[i][j][0] = 0x000;  // Min
                stickCalibration[i][j][1] = 0x800;  // Center
                stickCalibration[i][j][2] = 0x1000;  // Max

                stickExtends[i][j][0] = -0x700;
                stickExtends[i][j][1] = 0x700;
            }
        }
    }

    private float applyStickCalibration(int value, int stick, int axis) {
        if (value < 0) {
            value += 0x1000;
        }
        int center = stickCalibration[stick][axis][1];

        value -= center;

        if (value < stickExtends[stick][axis][0]) {
            stickExtends[stick][axis][0] = value;
            return -1;
        } else if (value > stickExtends[stick][axis][1]) {
            stickExtends[stick][axis][1] = value;
            return 1;
        }

        if (value > 0) {
            return value / stickExtends[stick][axis][1];
        } else {
            return -value / stickExtends[stick][axis][0];
        }
    }

    private void enableIMU(boolean enable) {
        byte[] data = new byte[11];
        data[0] = 0x01;  // Rumble subcommand
        data[1] = 0x00;  // Subcommand counter (can increment)
        System.arraycopy(RUMBLE_NEUTRAL, 0, data, 2, RUMBLE_NEUTRAL.length);
        System.arraycopy(RUMBLE_NEUTRAL, 0, data, 6, RUMBLE_NEUTRAL.length);
        data[10] = (byte) (enable ? 0x01 : 0x00);  // Enable or disable IMU

        sendSubcommand((byte) 0x40, data);
    }

    private void enableVibration(boolean enable) {
        byte[] data = new byte[11];
        data[0] = 0x01;  // Rumble subcommand
        data[1] = 0x00;  // Subcommand counter
        System.arraycopy(RUMBLE_NEUTRAL, 0, data, 2, RUMBLE_NEUTRAL.length);
        System.arraycopy(RUMBLE_NEUTRAL, 0, data, 6, RUMBLE_NEUTRAL.length);
        data[10] = (byte) (enable ? 0x01 : 0x00);  // Enable or disable vibration

        sendSubcommand((byte) 0x48, data);
    }

    private boolean sendSubcommand(byte subcommand, byte[] payload) {
        byte[] packet = new byte[PACKET_SIZE];
        packet[0] = 0x01;  // Rumble command
        packet[1] = 0x00;  // Counter (increments on each call)
        System.arraycopy(payload, 0, packet, 2, payload.length);

        int result = connection.bulkTransfer(outEndpt, packet, packet.length, 100);
        return result == packet.length;
    }
}
