package com.genersoft.iot.vmp.ykd.SDK;

import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class InspectRobot {

    public enum Direction {Left, Right, Up, Down}

    public enum AxisType {Horizontal, Vertical}

    private int pulsesPerRevolution = 400; // 编码器每圈脉冲数
    private double wheelDiameter = 0.04;   // 驱动轮直径（米）
    private boolean isCircularTrack = false;  // 是否为环形轨道

    // 默认端口4196
    private String host;

    // 定义监听器
    @Setter
    private OnPresetReachedListener onPresetReachedListener;
    @Setter
    private OnPositionReceivedListener onHorizontalPositionReceivedListener;
    @Setter
    private OnPositionReceivedListener onVerticalPositionReceivedListener;

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private final byte[] receiveBuffer = new byte[4096];
    private final List<Byte> dataBuffer = new ArrayList<>(); // 累积接收数据的缓冲区

    private boolean expectingHigh = false; // 是否正在等待高16位数据
    private boolean expectingLow = false;  // 是否正在等待低16位数据

    private long currentHigh = 0; // 当前高16位脉冲值

    private ExecutorService executorService;
    private volatile boolean isConnected = false;

    private final Object lock = new Object();

    /**
     * 设备参数设置
     *
     * @param pulsesPerRevolution 编码器每圈脉冲数
     * @param wheelDiameter       驱动轮直径（米）
     * @param isCircularTrack     是否为环形轨道
     */
    public void setParameters(int pulsesPerRevolution, double wheelDiameter, boolean isCircularTrack) {
        this.pulsesPerRevolution = pulsesPerRevolution;
        this.wheelDiameter = wheelDiameter;
        this.isCircularTrack = isCircularTrack;
    }

    /**
     * 到达预设位置的监听器接口
     */
    public interface OnPresetReachedListener {
        void onPresetReached(String host, int presetNumber, AxisType axisType);
    }

    /**
     * 位置数据接收的监听器接口
     */
    public interface OnPositionReceivedListener {
        void onPositionReceived(String host, double position);
    }

    /**
     * 连接到轨道机
     *
     * @param ipAddress IP地址
     * @param port      端口，默认4196
     * @throws IOException 如果连接失败
     */
    public void connect(String ipAddress, int port) throws IOException {
        synchronized (lock) {
            socket = new Socket(InetAddress.getByName(ipAddress), port);
            socket.setTcpNoDelay(false);
            socket.setSoTimeout(15000); // 15秒超时
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            System.out.println("Connected to device.");

            isConnected = true;

            this.host = ipAddress + ":" + port;

            // 启动异步接收线程
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(this::receiveDataAsync);
        }
    }

    /**
     * 发送Pelco D指令
     *
     * @param address 地址
     * @param cmd1    命令1
     * @param cmd2    命令2
     * @param data1   数据1
     * @param data2   数据2
     * @throws IOException 如果发送失败
     */
    public void sendCommand(byte address, byte cmd1, byte cmd2, byte data1, byte data2) throws IOException {
        synchronized (lock) {
            byte[] command = buildPelcoDCommand(address, cmd1, cmd2, data1, data2);
            outputStream.write(command);
            outputStream.flush();
            System.out.println("Sent: " + bytesToHex(command));
        }
    }

    /**
     * 构建Pelco D指令（含校验位计算）
     *
     * @param address 地址
     * @param cmd1    命令1
     * @param cmd2    命令2
     * @param data1   数据1
     * @param data2   数据2
     * @return 完整的命令字节数组
     */
    private byte[] buildPelcoDCommand(byte address, byte cmd1, byte cmd2, byte data1, byte data2) {
        byte[] buffer = new byte[7];
        buffer[0] = (byte) 0xFF;  // 标志位
        buffer[1] = address;
        buffer[2] = cmd1;
        buffer[3] = cmd2;
        buffer[4] = data1;
        buffer[5] = data2;

        // 计算校验位：字节1至字节5之和取余256
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            sum += buffer[i] & 0xFF;
        }
        buffer[6] = (byte) (sum % 256);

        return buffer;
    }

    /**
     * 异步接收数据
     */
    private void receiveDataAsync() {
        try {
            while (isConnected && socket != null && !socket.isClosed()) {
                int bytesRead = inputStream.read(receiveBuffer);
                if (bytesRead <= 0) break; // 连接已关闭

                byte[] receivedData = Arrays.copyOfRange(receiveBuffer, 0, bytesRead);
                System.out.println("Received: " + bytesToHex(receivedData));

                // 将新数据追加到缓冲区
                for (int i = 0; i < bytesRead; i++) {
                    dataBuffer.add(receiveBuffer[i]);
                }

                // 解析回传数据
                parseResponse();
            }
        } catch (IOException ex) {
            System.out.println("Receive error: " + ex.getMessage());
        } finally {
            disconnect();
        }
    }

    /**
     * 移动控制操作
     *
     * @param direction 方向
     * @param enable    是否启用
     * @throws IOException 如果发送失败
     */
    public void move(Direction direction, boolean enable) throws IOException {
        byte data2;
        switch (direction) {
            case Left:
                data2 = 0x01;
                break;
            case Right:
                data2 = 0x02;
                break;
            case Up:
                data2 = 0x06;
                break;
            case Down:
                data2 = 0x05;
                break;
            default:
                throw new IllegalArgumentException("Invalid direction");
        }

        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                enable ? (byte) 0x09 : (byte) 0x0B,
                (byte) 0x00,
                data2
        );
    }

    /**
     * 灯光控制
     *
     * @param enable 是否启用
     * @throws IOException 如果发送失败
     */
    public void setLight(boolean enable) throws IOException {
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                enable ? (byte) 0x09 : (byte) 0x0B,
                (byte) 0x00,
                (byte) 0x03
        );
    }

    /**
     * 调速控制（高速模式）
     *
     * @param enable 是否启用
     * @throws IOException 如果发送失败
     */
    public void setHighSpeed(boolean enable) throws IOException {
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                enable ? (byte) 0x09 : (byte) 0x0B,
                (byte) 0x00,
                (byte) 0x04
        );
    }

    /**
     * 设置预置位
     *
     * @param presetNumber 预置位编号（1-255）
     * @throws IOException 如果发送失败
     */
    public void setPreset(byte presetNumber) throws IOException {
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0x03, // 设置预置位命令
                (byte) 0x00,
                presetNumber
        );
    }

    /**
     * 调用预置位
     *
     * @param presetNumber 预置位编号（1-255）
     * @throws IOException 如果发送失败
     */
    public void gotoPreset(byte presetNumber) throws IOException {
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0x07, // 调用预置位命令
                (byte) 0x00,
                presetNumber
        );
    }

    /**
     * 辅助控制指令（通用）
     *
     * @param functionCode 功能码（如雨刷、加热器等）
     * @param enable       是否启用
     * @throws IOException 如果发送失败
     */
    public void auxControl(byte functionCode, boolean enable) throws IOException {
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                enable ? (byte) 0x09 : (byte) 0x0B,
                (byte) 0x00,
                functionCode
        );
    }

    /**
     * 查询水平位置
     *
     * @throws IOException 如果发送失败
     */
    public void queryHorizontalPosition() throws IOException {
        // 文档指令：FF01 00EB 0000 EC
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0xEB,
                (byte) 0x00,
                (byte) 0x00
        );
    }

    /**
     * 查询垂直位置
     *
     * @throws IOException 如果发送失败
     */
    public void queryVerticalPosition() throws IOException {
        // 文档指令：FF01 00EA 0000 EB
        sendCommand(
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0xEA,
                (byte) 0x00,
                (byte) 0x00
        );
    }

    /**
     * 解析回传数据
     */
    private void parseResponse() {
        while (dataBuffer.size() >= 7) { // 最小帧长度为7个字节
            // 查找帧头 0xFF
            int startIndex = -1;
            for (int i = 0; i < dataBuffer.size(); i++) {
                if (dataBuffer.get(i) == (byte) 0xFF) {
                    startIndex = i;
                    break;
                }
            }

            if (startIndex == -1) {
                dataBuffer.clear(); // 无有效帧头，清空缓冲区
                return;
            }

            if (startIndex > 0) {
                // 移除帧头前的无效数据
                dataBuffer.subList(0, startIndex).clear();
                startIndex = 0;
            }

            // 检查是否具备完整帧长度
            if (dataBuffer.size() < 7) return; // 不足最小帧，等待更多数据

            // 提取前面7个字节尝试解析
            byte[] frame = new byte[7];
            for (int i = 0; i < 7; i++) {
                frame[i] = dataBuffer.get(i);
            }

            // 判断帧类型
            if (frame[1] == 0x1A || frame[1] == 0x1B) {
                // 预置位到达回传，直接处理
                handlePresetReached(frame);
                removeFrameFromBuffer(7);
            } else if (frame[1] == 0x01 && frame[2] == 0x00) {
                if (frame[3] == (byte) 0xDC || frame[3] == (byte) 0xDE) {
                    // 校验位验证
                    byte checksum = calculateChecksum(frame, 1, 5); // 字节2-6之和
                    if (frame[6] == checksum) {
                        // 查询位置的高位回传（水平或垂直）
                        handleHighPosition(frame);
                    }
                    removeFrameFromBuffer(7);
                } else if (frame[3] == (byte) 0xDB || frame[3] == (byte) 0xDF) {
                    // 校验位验证
                    byte checksum = calculateChecksum(frame, 1, 5); // 字节2-6之和
                    if (frame[6] == checksum) {
                        // 查询位置的低位回传（水平或垂直）
                        handleLowPosition(frame);
                    }
                    removeFrameFromBuffer(7);
                } else {
                    // 其他未知帧，跳过
                    removeFrameFromBuffer(7);
                }
            } else {
                // 其他未知帧，跳过
                removeFrameFromBuffer(7);
            }
        }
    }

    /**
     * 从缓冲区中移除帧数据
     *
     * @param length 要移除的长度
     */
    private void removeFrameFromBuffer(int length) {
        for (int i = 0; i < length && !dataBuffer.isEmpty(); i++) {
            dataBuffer.remove(0);
        }
    }

    /**
     * 处理预置位到达事件
     *
     * @param frame 接收到的帧数据
     */
    private void handlePresetReached(byte[] frame) {
        int presetNum = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        AxisType direction = (frame[1] == 0x1A) ? AxisType.Horizontal : AxisType.Vertical;

        if (onPresetReachedListener != null) {
            onPresetReachedListener.onPresetReached(host, presetNum, direction);
        }
    }

    /**
     * 处理高位数据
     *
     * @param frame 接收到的帧数据
     */
    private void handleHighPosition(byte[] frame) {
        // 提取高16位脉冲值
        long high = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        currentHigh = high;
        expectingLow = true; // 标记需要等待低位数据
    }

    /**
     * 处理低位数据
     *
     * @param frame 接收到的帧数据
     */
    private void handleLowPosition(byte[] frame) {
        if (!expectingLow) return; // 未期待低位数据，丢弃

        // 提取低16位脉冲值
        long low = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        long totalPulses = (currentHigh << 16) | low;

        // 触发回调事件
        if (frame[3] == (byte) 0xDB) {
            if (onHorizontalPositionReceivedListener != null) {
                onHorizontalPositionReceivedListener.onPositionReceived(
                        host,
                        calculateHorizontalDistance(totalPulses));
            }
        } else if (frame[3] == (byte) 0xDF) {
            if (onVerticalPositionReceivedListener != null) {
                onVerticalPositionReceivedListener.onPositionReceived(
                        host,
                        calculateVerticalDistance(totalPulses));
            }
        }

        expectingLow = false; // 重置状态
    }

    /**
     * 脉冲值转水平距离
     *
     * @param pulses 脉冲数
     * @return 水平距离（米）
     */
    private double calculateHorizontalDistance(long pulses) {
        double revolutions = pulses / (double) pulsesPerRevolution;
        double distance = revolutions * Math.PI * wheelDiameter;

        // 如果是环形轨道，需除以3（文档示例）
        return isCircularTrack ? distance / 3 : distance;
    }

    /**
     * 脉冲值转垂直高度
     *
     * @param pulses 脉冲数
     * @return 垂直距离（米）
     */
    private double calculateVerticalDistance(long pulses) {
        double revolutions = pulses / (double) pulsesPerRevolution;
        return revolutions * Math.PI * wheelDiameter;
    }

    /**
     * 计算校验和
     *
     * @param data   数据数组
     * @param start  起始索引
     * @param length 长度
     * @return 校验和
     */
    private byte calculateChecksum(byte[] data, int start, int length) {
        int sum = 0;
        for (int i = start; i < start + length; i++) {
            sum += data[i] & 0xFF;
        }
        return (byte) (sum % 256);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        synchronized (lock) {
            isConnected = false;

            if (executorService != null) {
                executorService.shutdown();
            }

            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.out.println("Error during disconnect: " + e.getMessage());
            }

            System.out.println("Disconnected.");
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString();
    }

    /// 心跳检测
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    private volatile boolean reconnecting = false;

    // 自动重连标志
    public void startHeartbeatWithHorizontal(long queryIntervalMs, long heartbeatTimeoutMs) {
        // 设置位置回调（只需设置一次即可）
        this.setOnHorizontalPositionReceivedListener((host, pos) -> lastHeartbeat.set(System.currentTimeMillis()));

        // 每5秒发一次位置查询
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (isConnected) {
                    queryHorizontalPosition();
                }
            } catch (Exception e) {
                // 这里捕获异常，避免定时任务中断
                System.out.println("Error during heartbeat query1: " + e.getMessage());
                isConnected = false;
            }
        }, 0, queryIntervalMs, TimeUnit.MILLISECONDS);

        // 每1秒检查一次心跳超时
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!isConnected) return;
            long now = System.currentTimeMillis();
            if (now - lastHeartbeat.get() > heartbeatTimeoutMs && !reconnecting) {
                System.out.println("心跳超时，断线: " + host);
                isConnected = false;
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // 自动重连标志
    public void startHeartbeatWithVertical(long queryIntervalMs, long heartbeatTimeoutMs) {
        // 设置位置回调（只需设置一次即可）
        this.setOnVerticalPositionReceivedListener((host, pos) -> lastHeartbeat.set(System.currentTimeMillis()));

        // 每5秒发一次位置查询
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (isConnected) {
                    queryVerticalPosition();
                }
            } catch (Exception e) {
                // 这里捕获异常，避免定时任务中断
                System.out.println("Error during heartbeat query2: " + e.getMessage());
                isConnected = false;
            }
        }, 0, queryIntervalMs, TimeUnit.MILLISECONDS);

        // 每1秒检查一次心跳超时
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!isConnected) return;
            long now = System.currentTimeMillis();
            if (now - lastHeartbeat.get() > heartbeatTimeoutMs && !reconnecting) {
                System.out.println("心跳超时，断线: " + host);
                isConnected = false;
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public boolean getConnectionStatus() {
        synchronized (lock) {
            return isConnected;
        }
    }

    // 自动心跳+掉线重连功能
//    public void startHeartbeatWithAutoReconnect(long queryIntervalMs, long heartbeatTimeoutMs) {
//        // 设置位置回调（只需设置一次即可）
//        this.setOnHorizontalPositionReceivedListener((host, pos) -> lastHeartbeat.set(System.currentTimeMillis()));
//
//        // 每5秒发一次位置查询
//        heartbeatScheduler.scheduleAtFixedRate(() -> {
//            try {
//                if (isConnected) {
//                    queryHorizontalPosition();
//                }
//            } catch (Exception e) {
//                // 这里捕获异常，避免定时任务中断
//            }
//        }, 0, queryIntervalMs, TimeUnit.MILLISECONDS);
//
//        // 每1秒检查一次心跳超时
//        heartbeatScheduler.scheduleAtFixedRate(() -> {
//            if (!isConnected) return;
//            long now = System.currentTimeMillis();
//            if (now - lastHeartbeat.get() > heartbeatTimeoutMs && !reconnecting) {
//                System.out.println("心跳超时，准备断线重连: " + host);
//                reconnecting = true;
//                try {
//                    // 断开
//                    disconnect();
//                    Thread.sleep(500); // 稍微等待
//                    // 重连（你的连接参数保存在host里，可自行调整）
//                    String[] arr = host.split(":");
//                    String ip = arr[0];
//                    int port = Integer.parseInt(arr[1]);
//                    connect(ip, port);
//                    lastHeartbeat.set(System.currentTimeMillis());
//                    System.out.println("重连成功: " + host);
//                } catch (Exception ex) {
//                    System.out.println("重连失败: " + ex.getMessage());
//                } finally {
//                    reconnecting = false;
//                }
//            }
//        }, 1, 1, TimeUnit.SECONDS);
//    }

}
