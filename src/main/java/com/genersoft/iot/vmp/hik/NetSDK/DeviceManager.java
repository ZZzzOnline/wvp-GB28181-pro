package com.genersoft.iot.vmp.hik.NetSDK;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class DeviceManager {

    private int lUserID = -1; // 登录句柄
    private final DeviceContext deviceContext; // 设备上下文
    private final ConcurrentHashMap<Integer, StreamContext> streamContexts = new ConcurrentHashMap<>(); // 通道号到流上下文的映射

    private static final Logger logger = LoggerFactory.getLogger(DeviceManager.class);

    public DeviceManager(DeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            // 确保释放所有资源
            if (lUserID != -1) {
                logoutDevice();
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * 登录设备
     *
     * @param ip      设备IP地址
     * @param port    设备端口
     * @param account 登录账号
     * @param passwd  登录密码
     */
    public void loginDevice(String ip, short port, String account, String passwd) {
        if (lUserID != -1) {
            return;
        }
        // 调用登录逻辑
        lUserID = deviceContext.loginDevice(ip + ":" + port, port, account, passwd);
    }

    /**
     * 登出设备
     */
    public void logoutDevice() {
        if (lUserID == -1) {
            return; // 未登录
        }
        // 清理流上下文
        for (StreamContext context : streamContexts.values()) {
            // 停止保存流（如果正在保存）
            if (context.isSaving()) {
                try {
                    deviceContext.stopSaveRealData(context.getPlayHandle());
                } catch (Exception e) {
                    // 记录日志或处理异常
                    logger.error("停止保存流时发生异常: {}", e.getMessage(), e);
                }
                context.setSaving(false);
            }
            // 停止实时流
            try {
                deviceContext.stopRealStreamData(context.getPlayHandle());
            } catch (Exception e) {
                logger.error("停止流时发生异常: {}", e.getMessage(), e);
            }
        }
        // 调用登出逻辑
        try {
            deviceContext.logoutDevice(lUserID);
        } catch (Exception e) {
            logger.error("登出设备时发生异常: {}", e.getMessage(), e);
        }
        lUserID = -1;

        streamContexts.clear(); // 清空流上下文
    }

    public boolean checkUserID() {
        if (lUserID == -1) {
            return false; // 未登录
        }
        return deviceContext.checkUserID(lUserID);
    }

    public void captureJPEGPicture(int channel, String imagePath) {
        if (lUserID == -1) {
            throw new IllegalStateException("请先登录设备");
        }
        // 调用抓图逻辑
        deviceContext.captureJPEGPicture(lUserID, channel, imagePath);
    }

    public void captureJPEGPictureWithAppendData(int channel) {
        deviceContext.captureJPEGPictureWithAppendData(lUserID, channel);
    }

    /**
     * 获取实时流数据
     *
     * @param channel 通道号
     */
    public void getRealStreamData(int channel) {
        if (lUserID == -1) {
            throw new IllegalStateException("请先登录设备");
        }
        if (streamContexts.containsKey(channel)) {
            return;
        }
        // 调用取流逻辑
        int playHandle = deviceContext.getRealStreamData(lUserID, channel);
        streamContexts.put(channel, new StreamContext(playHandle));
    }

    /**
     * 停止实时流数据
     *
     * @param channel 通道号
     */
    public void stopRealStreamData(int channel) {
        StreamContext context = streamContexts.get(channel);
        if (context == null) {
            return;
        }
        // 停止保存流（如果正在保存）
        if (context.isSaving()) {
            deviceContext.stopSaveRealData(context.getPlayHandle());
        }
        // 停止实时流
        deviceContext.stopRealStreamData(context.getPlayHandle());
        streamContexts.remove(channel);
    }

    /**
     * 开始保存实时数据
     *
     * @param channel   通道号
     * @param videoPath 保存路径
     */
    public void startSaveRealData(int channel, String videoPath) {
        StreamContext context = streamContexts.get(channel);
        if (context == null) {
            throw new IllegalStateException("该通道未开启实时流");
        }
        if (context.isSaving()) {
            return;
        }
        // 调用保存流逻辑
        deviceContext.startSaveRealData(context.getPlayHandle(), videoPath);
        context.setSaving(true);
    }

    /**
     * 停止保存实时数据
     *
     * @param channel 通道号
     */
    public void stopSaveRealData(int channel) {
        StreamContext context = streamContexts.get(channel);
        if (context == null || !context.isSaving()) {
            return;
        }
        deviceContext.stopSaveRealData(context.getPlayHandle());
        context.setSaving(false);
    }

    // 内部类：流上下文
    private static class StreamContext {
        @Getter
        private final int playHandle; // 实时流句柄
        private boolean isSaving; // 是否正在保存

        public StreamContext(int playHandle) {
            this.playHandle = playHandle;
            this.isSaving = false;
        }

        public boolean isSaving() {
            return isSaving;
        }

        public void setSaving(boolean saving) {
            isSaving = saving;
        }
    }
}
