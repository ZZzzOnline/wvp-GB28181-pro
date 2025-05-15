package com.genersoft.iot.vmp.hik.NetSDK;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.module.business.hik.NetSDK.HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN;
import static cn.iocoder.yudao.module.business.hik.NetSDK.SDKMgr.hCNetSDK;
import static cn.iocoder.yudao.module.business.hik.NetSDK.SDKMgr.playControl;

public class DeviceContext {

    /**
     * 登录设备，支持 V40 和 V30 版本，功能一致。
     *
     * @param ip   设备IP地址
     * @param port SDK端口，默认为设备的8000端口
     * @param user 设备用户名
     * @param psw  设备密码
     * @return 登录成功返回用户ID，失败返回-1
     */
    public int loginDevice(String ip, short port, String user, String psw) {
        // 创建设备登录信息和设备信息对象
        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();

        // 设置设备IP地址
        byte[] deviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        byte[] ipBytes = ip.getBytes();
        System.arraycopy(ipBytes, 0, deviceAddress, 0, Math.min(ipBytes.length, deviceAddress.length));
        loginInfo.sDeviceAddress = deviceAddress;

        // 设置用户名和密码
        byte[] userName = new byte[NET_DVR_LOGIN_USERNAME_MAX_LEN];
        byte[] password = psw.getBytes();
        System.arraycopy(user.getBytes(), 0, userName, 0, Math.min(user.length(), userName.length));
        System.arraycopy(password, 0, loginInfo.sPassword, 0, Math.min(password.length, loginInfo.sPassword.length));
        loginInfo.sUserName = userName;

        // 设置端口和登录模式
        loginInfo.wPort = port;
        loginInfo.bUseAsynLogin = false; // 同步登录
        loginInfo.byLoginMode = 0; // 使用SDK私有协议

        // 执行登录操作
        int userID = hCNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);
        if (userID == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            System.out.println("登录失败，错误码: " + errorCode);
            throw new RuntimeException("登录失败，错误码: " + errorCode);
        }

        System.out.println("设备登录成功！userID:" + userID + " IP:" + ip);
        // 处理通道号逻辑
        int startDChan = deviceInfo.struDeviceV30.byStartDChan;
        System.out.println("预览起始通道号: " + startDChan);

        return userID; // 返回登录结果
    }

    /**
     * 退出登录设备
     *
     * @param lUserID 登录的用户ID
     */
    public void logoutDevice(int lUserID) {
        //退出程序时调用，每一台设备分别注销
        if (hCNetSDK.NET_DVR_Logout(lUserID)) {
            System.out.println("设备登出成功 lUserID: " + lUserID);
        } else {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            System.out.println("设备登出失败，错误码: " + errorCode);
            throw new RuntimeException("设备登出失败，错误码: " + errorCode);
        }
    }

    /**
     * 检查用户ID是否有效
     *
     * @param lUserID 登录的用户ID
     * @return true 如果用户ID有效，false 如果无效
     */
    public boolean checkUserID(int lUserID) {
        // 尝试获取设备时间（轻量级操作，用于检测 userID 是否有效）
        HCNetSDK.NET_DVR_TIME timeStruct = new HCNetSDK.NET_DVR_TIME();
        IntByReference returnedBytes = new IntByReference(0);
        boolean ret = hCNetSDK.NET_DVR_GetDVRConfig(
                lUserID,                            // 用户登录ID
                HCNetSDK.NET_DVR_GET_TIMECFG,       // 命令码
                0,                                  // 通道号，此处为0
                timeStruct.getPointer(),            // 接收数据的缓冲区指针
                timeStruct.size(),                  // 接收数据的缓冲区大小
                returnedBytes                       // 返回的数据大小
        );
        // 从内存读取结构体数据
        timeStruct.read();

        if (!ret) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            IntByReference errorNo = new IntByReference(errorCode);
            String errorMsg = hCNetSDK.NET_DVR_GetErrorMsg(errorNo);
            System.out.println("错误码：" + errorCode + "，错误信息：" + errorMsg);
            // 重新登录设备
            if (lUserID != -1) {
                return false;
            }
            return false;
        } else {
            System.out.println("userID:" + lUserID + " 仍然有效");
            // 使用获取到的时间数据
            System.out.println("设备时间：" + timeStruct.dwYear + "-" +
                    timeStruct.dwMonth + "-" +
                    timeStruct.dwDay + " " +
                    timeStruct.dwHour + ":" +
                    timeStruct.dwMinute + ":" +
                    timeStruct.dwSecond);
            return true;
        }
    }

    /**
     * 抓图
     *
     * @param lUserID   登录的用户ID
     * @param channel   通道号
     * @param imagePath 图片存储路径
     */
    public void captureJPEGPicture(int lUserID, int channel, String imagePath) {
        HCNetSDK.NET_DVR_JPEGPARA net_dvr_jpegpara = new HCNetSDK.NET_DVR_JPEGPARA();
        net_dvr_jpegpara.wPicQuality = 0;
        net_dvr_jpegpara.wPicSize = 0xff;

        try {
            byte[] byPath = imagePath.getBytes("GBK");
            if (!hCNetSDK.NET_DVR_CaptureJPEGPicture(lUserID, channel, net_dvr_jpegpara, byPath)) {
                int errorCode = hCNetSDK.NET_DVR_GetLastError();
                System.out.println("抓图失败，错误代码为：" + errorCode);
                throw new RuntimeException("抓图失败，错误代码为：" + errorCode);
            }
            System.out.println("抓图成功 lUserID: " + lUserID + " channel: " + channel + " imagePath: " + imagePath);
        } catch (UnsupportedEncodingException e) {
            System.out.println("路径编码失败: " + e.getMessage());
            throw new RuntimeException("路径编码失败: " + e.getMessage(), e);
        } catch (Exception e) {
            System.out.println("抓图过程中发生未知错误: " + e.getMessage());
            throw new RuntimeException("抓图过程中发生未知错误: " + e.getMessage(), e);
        }
    }

    /**
     * 抓图，支持附加数据
     *
     * @param lUserID 登录的用户ID
     * @param channel 通道号
     */
    public void captureJPEGPictureWithAppendData(int lUserID, int channel) {
        // 创建结构体
        HCNetSDK.NET_DVR_JPEGPICTURE_WITH_APPENDDATA struJpegPictureWithAppendData = new HCNetSDK.NET_DVR_JPEGPICTURE_WITH_APPENDDATA();

        // 分配热成像图像缓冲区 (2MB)
        Memory jpegPicBuff = new Memory(2 * 1024 * 1024);
        jpegPicBuff.clear();
        struJpegPictureWithAppendData.pJpegPicBuff = jpegPicBuff;

        // 分配测温数据缓冲区 (2MB)
        Memory p2pDataBuff = new Memory(2 * 1024 * 1024);
        p2pDataBuff.clear();
        struJpegPictureWithAppendData.pP2PDataBuff = p2pDataBuff;

        // 分配可见光图像缓冲区 (10MB)
//        Memory visiblePicBuff = new Memory(10 * 1024 * 1024); // 可见光图至少为4M
//        visiblePicBuff.clear();
//        struJpegPictureWithAppendData.pVisiblePicBuff = visiblePicBuff;

        // 调用SDK捕获图片函数
        boolean result = hCNetSDK.NET_DVR_CaptureJPEGPicture_WithAppendData(lUserID, channel, struJpegPictureWithAppendData);
        if (!result) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            IntByReference errorNo = new IntByReference(errorCode);
            String errorMsg = hCNetSDK.NET_DVR_GetErrorMsg(errorNo);
            System.out.println("捕获失败，错误码：" + errorCode + "，错误信息：" + errorMsg);
            return;
        }

        // 获取当前时间作为文件名
        String timeStr = new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS").format(new java.util.Date());
        String basePath = "./pic"; // 替换为实际的保存路径
        String deviceIP = "192.168.1.1";  // 替换为实际的设备IP

        // 创建目录
        java.io.File directory = new java.io.File(basePath + "\\" + deviceIP + "[Jpegwithappend]");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 保存P2P数据
        if (struJpegPictureWithAppendData.dwP2PDataLen > 0) {
            String p2pFilePath = directory.getPath() + "\\P2PData_[" + timeStr + "]_" + channel + "_Freeze[" + struJpegPictureWithAppendData.byIsFreezedata + "].data";
            saveDataToFile(struJpegPictureWithAppendData.pP2PDataBuff, struJpegPictureWithAppendData.dwP2PDataLen, p2pFilePath);
        }

        // 保存热成像图片
        if (struJpegPictureWithAppendData.dwJpegPicLen > 0) {
            String thermalPicPath = directory.getPath() + "\\ThermalPic[" + timeStr + "]_" + channel + ".jpg";
            saveDataToFile(struJpegPictureWithAppendData.pJpegPicBuff, struJpegPictureWithAppendData.dwJpegPicLen, thermalPicPath);
        }

        // 保存可见光图片
//        if (struJpegPictureWithAppendData.dwVisiblePicLen > 0) {
//            String visiblePicPath = directory.getPath() + "\\VisiblePic[" + timeStr + "]_" + channel + ".jpg";
//            saveDataToFile(struJpegPictureWithAppendData.pVisiblePicBuff, struJpegPictureWithAppendData.dwVisiblePicLen, visiblePicPath);
//        }

        processTemperatureData(struJpegPictureWithAppendData);
    }

    private void saveDataToFile(Pointer pointer, int length, String filePath) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath);
            byte[] buffer = pointer.getByteArray(0, length);
            fos.write(buffer);
            fos.close();
            System.out.println("成功保存文件: " + filePath);
        } catch (java.io.IOException e) {
            System.out.println("保存文件失败: " + e.getMessage());
        }
    }

    private void processTemperatureData(HCNetSDK.NET_DVR_JPEGPICTURE_WITH_APPENDDATA data) {
        if (data.dwP2PDataLen <= 0) {
            System.out.println("没有温度数据");
            return;
        }

        // 获取P2P数据字节数组
        byte[] p2pData = data.pP2PDataBuff.getByteArray(0, data.dwP2PDataLen);

        float maxTemp = Float.MIN_VALUE;
        float minTemp = Float.MAX_VALUE;
        float sumTemp = 0;
        int validPoints = 0;

        // 每4个字节表示一个浮点温度值
        for (int i = 0; i < p2pData.length; i += 4) {
            if (i + 4 <= p2pData.length) {
                int tempInt = ((p2pData[i + 3] & 0xFF) << 24) |
                        ((p2pData[i + 2] & 0xFF) << 16) |
                        ((p2pData[i + 1] & 0xFF) << 8) |
                        (p2pData[i] & 0xFF);
                float temp = Float.intBitsToFloat(tempInt);

                // 过滤无效温度值(通常小于-40或大于200的值为无效值)
                if (temp > -20 && temp < 150) {
                    maxTemp = Math.max(maxTemp, temp);
                    minTemp = Math.min(minTemp, temp);
                    sumTemp += temp;
                    validPoints++;
                }
            }
        }

        float avgTemp = validPoints > 0 ? sumTemp / validPoints : 0;

        System.out.printf("最高温度: %.2f°C\n", maxTemp);
        System.out.printf("最低温度: %.2f°C\n", minTemp);
        System.out.printf("平均温度: %.2f°C\n", avgTemp);
    }

    /**
     * 开始实时取流
     *
     * @param lUserID    登录的用户ID
     * @param iChannelNo 通道号
     * @return 预览句柄
     */
    public int getRealStreamData(int lUserID, int iChannelNo) {
        if (lUserID == -1) {
            System.out.println("请先登录");
            throw new IllegalArgumentException("请先登录");
        }
        HCNetSDK.NET_DVR_PREVIEWINFO previewInfo = new HCNetSDK.NET_DVR_PREVIEWINFO();
        previewInfo.read();
        previewInfo.hPlayWnd = null;  // 窗口句柄，从回调取流不显示一般设置为空
        previewInfo.lChannel = iChannelNo;  // 通道号
        previewInfo.dwStreamType = 0; // 0-主码流，1-子码流，2-三码流，3-虚拟码流，以此类推
        previewInfo.dwLinkMode = 0; // 连接方式：0- TCP方式，1- UDP方式，2- 多播方式，3- RTP方式，4- RTP/RTSP，5- RTP/HTTP，6- HRUDP（可靠传输） ，7- RTSP/HTTPS，8- NPQ
        previewInfo.bBlocked = 1;  // 0- 非阻塞取流，1- 阻塞取流
        previewInfo.byProtoType = 0; // 应用层取流协议：0- 私有协议，1- RTSP协议
        previewInfo.write();

        // 回调函数定义必须是全局的
        FRealDataCallBack fRealDataCallBack = new FRealDataCallBack(); // 预览回调函数实现
        UserData userData = new UserData();
        userData.write();

        // 开启预览
        int handle = hCNetSDK.NET_DVR_RealPlay_V40(lUserID, previewInfo, fRealDataCallBack, userData.getPointer());
        if (handle == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            System.out.println("取流失败，错误码: " + errorCode);
            throw new RuntimeException("取流失败，错误码: " + errorCode);
        }

        System.out.println("开启实时取流成功" + " lUserID: " + lUserID + " channel: " + iChannelNo + " playHandle: " + handle);
        return handle;
    }

    /**
     * 停止实时取流
     *
     * @param playHandle 预览句柄
     */
    public void stopRealStreamData(int playHandle) {
        if (playHandle == -1) {
            System.out.println("实时取流未开启，请先开启实时取流");
            return;
        }
        if (!hCNetSDK.NET_DVR_StopRealPlay(playHandle)) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            System.out.println("停止取流失败，错误码: " + errorCode);
            throw new RuntimeException("停止取流失败，错误码: " + errorCode);
        }
        System.out.println("停止实时取流成功" + " playHandle: " + playHandle);
    }

    /**
     * 开始保存实时数据
     *
     * @param playHandle 预览句柄
     * @param videoPath  视频存储路径
     */
    public void startSaveRealData(int playHandle, String videoPath) {
        if (playHandle == -1) {
            System.out.println("实时取流未开启，请先开启实时取流");
            throw new IllegalArgumentException("实时取流未开启，请先开启实时取流");
        }
        try {
            String path = System.getProperty("user.dir") + videoPath;
            byte[] byPath = path.getBytes("GBK");
            HCNetSDK.BYTE_ARRAY byArray = new HCNetSDK.BYTE_ARRAY(byPath.length);
            System.arraycopy(byPath, 0, byArray.byValue, 0, byPath.length);
            byArray.write();

            boolean bSaveVideo = hCNetSDK.NET_DVR_SaveRealData_V30(playHandle, 0x2, byArray.getPointer());
            if (!bSaveVideo) {
                int iErr = hCNetSDK.NET_DVR_GetLastError();
                System.out.println("NET_DVR_SaveRealData_V30 failed, error code: " + iErr);
                throw new RuntimeException("NET_DVR_SaveRealData_V30 failed, error code: " + iErr);
            }
            System.out.println("开启保存实时流数据成功" + " playHandle: " + playHandle + " videoPath: " + videoPath);
        } catch (UnsupportedEncodingException e) {
            System.out.println("路径编码失败: " + e.getMessage());
            throw new RuntimeException("路径编码失败: " + e.getMessage(), e);
        } catch (Exception e) {
            System.out.println("保存实时数据过程中发生未知错误: " + e.getMessage());
            throw new RuntimeException("保存实时数据过程中发生未知错误: " + e.getMessage(), e);
        }
    }

    /**
     * 停止保存实时数据
     *
     * @param playHandle 预览句柄
     */
    public void stopSaveRealData(int playHandle) {
        if (playHandle == -1) {
            System.out.println("实时取流未开启，请先开启实时取流");
            return;
        }
        if (!hCNetSDK.NET_DVR_StopSaveRealData(playHandle)) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            IntByReference errorNo = new IntByReference(errorCode);
            String errorMsg = hCNetSDK.NET_DVR_GetErrorMsg(errorNo);
            System.out.println("NET_DVR_StopSaveRealData 错误码：" + errorCode + "，错误信息：" + errorMsg);

            throw new RuntimeException("NET_DVR_StopSaveRealData 错误码：" + errorCode + "，错误信息：" + errorMsg);
        }
        System.out.println("停止保存实时流数据成功" + " playHandle: " + playHandle);
    }

    // 用户数据结构体
    public static class UserData extends Structure {
        public int id;
        public String name;
        public IntByReference lPort; // 整数指针

        // 必须定义字段顺序
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("id", "name", "lPort");
        }

        // 默认构造函数
        public UserData() {
            super();
            // 必须初始化引用类型字段
            lPort = new IntByReference(-1);
        }

        // 从Pointer构造
        public UserData(Pointer p) {
            super();
            useMemory(p); // 使用指针初始化内存
            lPort = new IntByReference();
            read(); // 必须调用 read() 加载数据
        }
    }

    // 预览回调函数
    static class FRealDataCallBack implements HCNetSDK.FRealDataCallBack_V30 {
        //预览回调
        public void invoke(int lRealHandle, int dwDataType, Pointer pBuffer, int dwBufSize, Pointer pUser) {
            // 首先检查 pUser 是否为空
            if (pUser == null) {
                System.out.println("用户数据指针为空");
                return;
            }
            UserData data = new UserData(pUser);
            IntByReference lPort = data.lPort;
            // 检查 lPort
            if (lPort == null) {
                System.out.println("lPort 为空");
                return;
            }
            switch (dwDataType) {
                case HCNetSDK.NET_DVR_SYSHEAD: //系统头
                {
                    if (!playControl.PlayM4_GetPort(lPort)) //获取播放库未使用的通道号
                    {
                        break;
                    }
                    System.out.println("获取播放库未使用的通道号:" + lPort.getValue());
                    if (dwBufSize > 0) {
                        if (!playControl.PlayM4_SetStreamOpenMode(lPort.getValue(), PlayCtrl.STREAME_REALTIME))  //设置实时流播放模式
                        {
                            break;
                        }
                        if (!playControl.PlayM4_OpenStream(lPort.getValue(), pBuffer, dwBufSize, 1024 * 1024)) //打开流接口
                        {
                            break;
                        }
                        if (!playControl.PlayM4_Play(lPort.getValue(), null)) //播放开始
                        {
                            break;
                        }
                    }
                }
                case HCNetSDK.NET_DVR_STREAMDATA:   //码流数据
                {
                    System.out.println("码流数据 lRealHandle:" + lRealHandle + " 通道号:" + lPort.getValue());
                    if ((dwBufSize > 0) && (lPort.getValue() != -1)) {
                        if (!playControl.PlayM4_InputData(lPort.getValue(), pBuffer, dwBufSize))  //输入流数据
                        {
                            break;
                        }
                    }
                }
            }
        }
    }

}
