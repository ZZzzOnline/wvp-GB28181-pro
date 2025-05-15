package com.genersoft.iot.vmp.hik.NetSDK;

public class BusinessManager {

    public static void main(String[] args) {
        DevicesManager devicesManager = DevicesManager.instance;
        // 添加设备
        devicesManager.addDeviceManager("192.168.3.244", new DeviceManager(new DeviceContext()));
        // 获取设备上下文
        DeviceManager deviceManager = devicesManager.getDeviceManager("192.168.3.244");
        // 登录
        deviceManager.loginDevice("192.168.3.244", (short) 8000, "admin", "abc12345");
        // 抓图
        deviceManager.captureJPEGPicture(1, "./pic/test_244_1.jpeg");
        deviceManager.captureJPEGPicture(2, "./pic/test_244_2.jpeg");
//        deviceManager.captureJPEGPictureWithAppendData(2);
        // 预览
        deviceManager.getRealStreamData(1);
        deviceManager.getRealStreamData(2);
        // 保存
        deviceManager.startSaveRealData(1, "/video/test244_1.mp4");
        deviceManager.startSaveRealData(2, "/video/test244_2.mp4");
        // 暂停
        try {
            Thread.sleep(1000 * 10); // 20秒
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 检查用户ID
        if (!deviceManager.checkUserID()) {
            // 重新登录
            deviceManager.loginDevice("192.168.3.244", (short) 8000, "admin", "abc12345");
        }
        // 停止保存
        deviceManager.stopSaveRealData(1);
        deviceManager.stopSaveRealData(2);
        // 停止预览
        deviceManager.stopRealStreamData(1);
        deviceManager.stopRealStreamData(2);
        // 登出
        deviceManager.logoutDevice();
        // 删除设备
        devicesManager.removeDeviceManager("192.168.3.244");
    }
}
