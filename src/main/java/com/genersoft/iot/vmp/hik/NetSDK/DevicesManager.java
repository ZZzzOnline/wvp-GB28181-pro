package com.genersoft.iot.vmp.hik.NetSDK;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class DevicesManager {

    // 单例实例
    public static final DevicesManager instance = new DevicesManager();

    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(DevicesManager.class);

    // 私有构造函数
    private DevicesManager() {
        // 初始化SDK
        SDKMgr.init();
    }

    // 析构方法
    @Override
    protected void finalize() throws Throwable {
        try {
            // 清理SDK
            SDKMgr.cleanup();
        } finally {
            super.finalize();
        }
    }

    // 使用线程安全的字典来存放已连接设备标识和设备上下文
    private final ConcurrentHashMap<String, DeviceManager> devicesManager = new ConcurrentHashMap<>();

    // 获取设备上下文
    public DeviceManager getDeviceManager(String host) {
        return devicesManager.get(host);
    }

    // 添加设备上下文
    public void addDeviceManager(String host, DeviceManager deviceManager) {
        devicesManager.put(host, deviceManager);
    }

    // 删除设备上下文
    public void removeDeviceManager(String host) {
        // 释放设备上下文
        DeviceManager deviceManager = devicesManager.get(host);
        try {
            deviceManager.logoutDevice();
        } catch (Exception e) {
            logger.error("Failed to logout device: {}", host, e);
        } finally {
            devicesManager.remove(host);
        }
    }

    // TODO:心跳检测设备在线状态，是否需要重连，清理离线设备上下文和重置数据库中的设备及其功能状态。（应该放到业务层来做）

    public static void main(String[] args) {
        DevicesManager devicesManager = DevicesManager.instance;
        // 添加设备
        devicesManager.addDeviceManager("192.168.3.244", new DeviceManager(new DeviceContext()));
        // 删除设备
        devicesManager.removeDeviceManager("192.168.3.244");
    }
}
