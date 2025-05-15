package com.genersoft.iot.vmp.hik.NetSDK;

import cn.iocoder.yudao.module.business.hik.Common.osSelect;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

public class SDKMgr {
    // 单例实例
//    private static volatile SDKMgr instance;
    static HCNetSDK hCNetSDK = null;
    static PlayCtrl playControl = null;
    static FExceptionCallBack_Imp fExceptionCallBack;
    // 用户ID与Session的映射
//    private final Map<Long, ClientDemo> userMap = new ConcurrentHashMap<>();

    // 获取单例实例的方法
//    public static SDKMgr getInstance() {
//        if (instance == null) {
//            synchronized (SDKMgr.class) {
//                if (instance == null) {
//                    instance = new SDKMgr();
//                }
//            }
//        }
//        return instance;
//    }

    static class FExceptionCallBack_Imp implements HCNetSDK.FExceptionCallBack {
        public void invoke(int dwType, int lUserID, int lHandle, Pointer pUser) {
            System.out.printf("异常事件类型: %d, 异常事件用户ID: %d, 异常事件句柄: %d, 异常事件用户参数: %s%n",
                    dwType, lUserID, lHandle, pUser);
            return;
        }
    }

    public static void init() {
        if (hCNetSDK == null && playControl == null) {
            if (!createSDKInstance()) {
                System.out.println("Load SDK fail");
                return;
            }
            if (!createPlayInstance()) {
                System.out.println("Load PlayCtrl fail");
                return;
            }
        }
        //linux系统建议调用以下接口加载组件库
        if (osSelect.isLinux()) {
            HCNetSDK.BYTE_ARRAY ptrByteArray1 = new HCNetSDK.BYTE_ARRAY(256);
            HCNetSDK.BYTE_ARRAY ptrByteArray2 = new HCNetSDK.BYTE_ARRAY(256);
            //这里是库的绝对路径，请根据实际情况修改，注意改路径必须有访问权限
            String strPath1 = System.getProperty("user.dir") + "/libs/hik/linux/libcrypto.so.1.1";
            String strPath2 = System.getProperty("user.dir") + "/libs/hik/linux/libssl.so.1.1";
            System.arraycopy(strPath1.getBytes(), 0, ptrByteArray1.byValue, 0, strPath1.length());
            ptrByteArray1.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_LIBEAY_PATH, ptrByteArray1.getPointer());
            System.arraycopy(strPath2.getBytes(), 0, ptrByteArray2.byValue, 0, strPath2.length());
            ptrByteArray2.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SSLEAY_PATH, ptrByteArray2.getPointer());
            String strPathCom = System.getProperty("user.dir") + "/libs/hik/linux/";
            HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
            System.arraycopy(strPathCom.getBytes(), 0, struComPath.sPath, 0, strPathCom.length());
            struComPath.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SDK_PATH, struComPath.getPointer());
        }
        //SDK初始化，一个程序只需要调用一次
        boolean initSuc = hCNetSDK.NET_DVR_Init();
        //异常消息回调
        if (fExceptionCallBack == null) {
            fExceptionCallBack = new FExceptionCallBack_Imp();
        }
        Pointer pUser = null;
        if (!hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, fExceptionCallBack, pUser)) {
            return;
        }
        System.out.println("设置异常消息回调成功");
        //启动SDK写日志
        hCNetSDK.NET_DVR_SetLogToFile(3, "./sdkLog", false);
    }

    private static boolean createSDKInstance() {
        if (hCNetSDK == null) {
            synchronized (HCNetSDK.class) {
                String strDllPath = "";
                try {
                    if (osSelect.isWindows())
                        //win系统加载库路径
                        strDllPath = System.getProperty("user.dir") + "\\libs\\hik\\win\\HCNetSDK.dll";

                    else if (osSelect.isLinux())
                        //Linux系统加载库路径
                        strDllPath = System.getProperty("user.dir") + "/libs/hik/linux/libhcnetsdk.so";
                    hCNetSDK = (HCNetSDK) Native.loadLibrary(strDllPath, HCNetSDK.class);
                } catch (Exception ex) {
                    System.out.println("loadLibrary: " + strDllPath + " Error: " + ex.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean createPlayInstance() {
        if (playControl == null) {
            synchronized (PlayCtrl.class) {
                String strPlayPath = "";
                try {
                    if (osSelect.isWindows()) {
                        //win系统加载库路径
                        strPlayPath = System.getProperty("user.dir") + "\\libs\\hik\\win\\PlayCtrl.dll";
                    } else if (osSelect.isLinux()) {
                        //Linux系统加载库路径
                        strPlayPath = System.getProperty("user.dir") + "/libs/hik/linux/libPlayCtrl.so";
                    }
                    playControl = (PlayCtrl) Native.loadLibrary(strPlayPath, PlayCtrl.class);

                } catch (Exception ex) {
                    System.out.println("loadLibrary: " + strPlayPath + " Error: " + ex.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    public static void cleanup() {
        if (hCNetSDK != null) {
            boolean b = hCNetSDK.NET_DVR_Cleanup();
            if (!b) {
                System.out.println("NET_DVR_Cleanup failed");
            }
            hCNetSDK = null;
        }
        if (playControl != null) {
            String strPlayPath = "";
            if (osSelect.isWindows()) {
                //win系统加载库路径
                strPlayPath = System.getProperty("user.dir") + "\\libs\\hik\\win\\PlayCtrl.dll";
            } else if (osSelect.isLinux()) {
                //Linux系统加载库路径
                strPlayPath = System.getProperty("user.dir") + "/libs/hik/linux/libPlayCtrl.so";
            }
            NativeLibrary.getInstance(strPlayPath).dispose();
            playControl = null;
        }
    }
}
