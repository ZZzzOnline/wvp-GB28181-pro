//package com.genersoft.iot.vmp.ykd;
//
//import com.genersoft.iot.vmp.ykd.SDK.InspectRobot;
//import com.genersoft.iot.vmp.ykd.SDK.InspectRobotsManager;
//import org.mybatis.logging.Logger;
//import org.mybatis.logging.LoggerFactory;
//import org.springframework.beans.factory.DisposableBean;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import java.util.Map;
//import java.util.concurrent.*;
//
//// DeviceConnectionManager.java
//@Service
//public class DeviceConnectionManager {
//    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DeviceConnectionManager.class);
//
//    private final Map<String, InspectRobot> activeConnections = new ConcurrentHashMap<>();
//    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
//
//    @Autowired
//    private DeviceInfoRepository deviceRepo;
//
//    @PostConstruct
//    public void init() {
//        scheduler.scheduleWithFixedDelay(this::checkDevices, 0, 30, TimeUnit.SECONDS);
//    }
//
//    /**
//     * 设备状态检查任务
//     */
//    private void checkDevices() {
//        try {
//            // 从数据库获取所有激活设备
//            List<DeviceInfo> devices = deviceRepo.findByActiveTrue();
//
//            // 处理新增设备
//            devices.stream()
//                    .filter(d -> !activeConnections.containsKey(d.getDeviceId()))
//                    .forEach(this::createConnection);
//
//            // 处理移除设备
//            Set<String> currentIds = devices.stream()
//                    .map(DeviceInfo::getDeviceId)
//                    .collect(Collectors.toSet());
//
//            activeConnections.keySet().stream()
//                    .filter(id -> !currentIds.contains(id))
//                    .forEach(this::removeConnection);
//        } catch (Exception e) {
//            logger.error("Device check task failed", e);
//        }
//    }
//
//    /**
//     * 创建设备连接
//     */
//    private void createConnection(DeviceInfo device) {
//        InspectRobot robot = new InspectRobot();
//        activeConnections.put(device.getDeviceId(), robot);
//
//        // 配置设备参数
//        robot.setParameters(
//                device.getPulsesPerRevolution(),
//                device.getWheelDiameter(),
//                device.isCircularTrack()
//        );
//
//        // 启动连接任务
//        startConnectionTask(device, robot);
//    }
//
//    /**
//     * 启动带退避策略的连接任务
//     */
//    private void startConnectionTask(DeviceInfo device, InspectRobot robot) {
//        AtomicInteger attempts = new AtomicInteger(0);
//
//        Runnable connectTask = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    if (robot.isConnected()) return;
//
//                    logger.info("Connecting to {}:{}", device.getIp(), device.getPort());
//                    robot.connect(device.getIp(), device.getPort());
//                    scheduleHeartbeat(device, robot);
//                } catch (IOException e) {
//                    handleConnectFailure(device, attempts.get());
//                }
//            }
//
//            private void handleConnectFailure(DeviceInfo device, int attempt) {
//                if (attempt >= device.getMaxReconnectAttempts()) {
//                    logger.error("Max reconnect attempts reached for {}", device.getDeviceId());
//                    return;
//                }
//
//                long delay = device.getReconnectBaseInterval() * (long) Math.pow(2, attempt);
//                scheduler.schedule(this, delay, TimeUnit.SECONDS);
//                attempts.incrementAndGet();
//                logger.warn("Connection failed, will retry in {}s (attempt {}/{})",
//                        delay, attempt+1, device.getMaxReconnectAttempts());
//            }
//        };
//
//        scheduler.submit(connectTask);
//    }
//
//    /**
//     * 定时发送心跳
//     */
//    private void scheduleHeartbeat(DeviceInfo device, InspectRobot robot) {
//        scheduler.scheduleAtFixedRate(() -> {
//            try {
//                if (robot.isConnected()) {
//                    robot.sendCommand((byte)0x01, (byte)0x00, (byte)0x00,
//                            (byte)0x00, (byte)0x00); // 示例心跳指令
//                }
//            } catch (IOException e) {
//                handleHeartbeatFailure(device, robot);
//            }
//        }, 0, device.getHeartbeatInterval(), TimeUnit.SECONDS);
//    }
//
//    private void handleHeartbeatFailure(DeviceInfo device, InspectRobot robot) {
//        logger.warn("Heartbeat failed for {}", device.getDeviceId());
//        robot.disconnect();
//        startConnectionTask(device, robot);
//    }
//
//    /**
//     * 移除设备连接
//     */
//    private void removeConnection(String deviceId) {
//        InspectRobot robot = activeConnections.remove(deviceId);
//        if (robot != null) {
//            robot.disconnect();
//            logger.info("Removed connection for {}", deviceId);
//        }
//    }
//
//    /**
//     * 获取设备实例
//     */
//    public InspectRobot getRobot(String deviceId) {
//        return activeConnections.get(deviceId);
//    }
//
//    @PreDestroy
//    public void shutdown() {
//        scheduler.shutdown();
//        activeConnections.values().forEach(InspectRobot::disconnect);
//    }
//}
