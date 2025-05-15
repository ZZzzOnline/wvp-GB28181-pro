package com.genersoft.iot.vmp.ykd.SDK;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class InspectRobotsManager {

    // 单例实例
    public static final InspectRobotsManager instance = new InspectRobotsManager();

    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(InspectRobotsManager.class);

    // 私有构造函数
    private InspectRobotsManager() {
        // 初始化SDK
    }

    // 析构方法
    @Override
    protected void finalize() throws Throwable {
        try {
            // 清理SDK
        } finally {
            super.finalize();
        }
    }

    // 使用线程安全的字典来存放已连接设备标识和设备上下文
    private final ConcurrentHashMap<String, InspectRobot> inspectRobotsManager = new ConcurrentHashMap<>();

    // 获取设备上下文
    public InspectRobot getInspectRobot(String host) {
        return inspectRobotsManager.get(host);
    }

    // 添加设备上下文
    public void addInspectRobot(String host, InspectRobot inspectRobot) {
        inspectRobotsManager.put(host, inspectRobot);
    }

    // 删除设备上下文
    public void removeInspectRobot(String host) {
        // 释放设备上下文
        InspectRobot inspectRobot = inspectRobotsManager.get(host);
        try {
            inspectRobot.disconnect();
        } catch (Exception e) {
            logger.error("Failed to logout device: {}", host, e);
        } finally {
            inspectRobotsManager.remove(host);
        }
    }

    // TODO:心跳检测设备在线状态，是否需要重连，清理离线设备上下文和重置数据库中的设备及其功能状态。（应该放到业务层来做）

    public static void main(String[] args) {
        InspectRobotsManager devicesManager = InspectRobotsManager.instance;
        // 添加设备
        devicesManager.addInspectRobot("192.168.3.244:4196", new InspectRobot());
        // 删除设备
        devicesManager.removeInspectRobot("192.168.3.244:4196");
    }
}
