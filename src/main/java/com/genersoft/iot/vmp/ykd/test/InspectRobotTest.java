package com.genersoft.iot.vmp.ykd.test;

import com.genersoft.iot.vmp.ykd.SDK.InspectRobot;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * YikedaRobotSDK 测试程序
 * 演示如何使用SDK的各项功能
 */
public class InspectRobotTest {
    private static InspectRobot robotSDK;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== YikedaRobot SDK 测试程序 ===");
        System.out.println("当前用户: zhaojihuionline");

        // 显示UTC时间
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        System.out.println("当前UTC时间: " + dateFormat.format(new Date()));

        robotSDK = new InspectRobot();

        // 设置监听器
        setupListeners();

        // 连接设备
        if (!connectToDevice()) {
            System.out.println("连接失败，程序退出");
            return;
        }

        // 显示主菜单
        showMainMenu();
    }

    private static void setupListeners() {
        // 设置预置位到达事件监听器
        robotSDK.setOnPresetReachedListener((host, presetNumber, axisType) -> {
            System.out.println("\n[事件] 预置位到达: #" + presetNumber +
                    " 轴: " + (axisType == InspectRobot.AxisType.Horizontal ? "水平" : "垂直"));
        });

        // 设置水平位置更新事件监听器
        robotSDK.setOnHorizontalPositionReceivedListener((host, position) -> {
            System.out.println("\n[事件] 收到水平位置: " + String.format("%.4f", position) + " 米");
        });

        // 设置垂直位置更新事件监听器
        robotSDK.setOnVerticalPositionReceivedListener((host, position) -> {
            System.out.println("\n[事件] 收到垂直位置: " + String.format("%.4f", position) + " 米");
        });
    }

    private static boolean connectToDevice() {
        System.out.print("请输入设备IP地址 (默认: 192.168.3.241): ");
        String ipAddress = scanner.nextLine().trim();
        if (ipAddress.isEmpty()) {
            ipAddress = "192.168.3.241";
        }

        System.out.print("请输入端口号 (默认: 4196): ");
        String portStr = scanner.nextLine().trim();
        int port = portStr.isEmpty() ? 4196 : Integer.parseInt(portStr);

        try {
            System.out.println("正在连接到 " + ipAddress + ":" + port + "...");
            robotSDK.connect(ipAddress, port);

            // 设置参数（如果需要自定义）
            robotSDK.setParameters(400, 0.04, false);
            return true;
        } catch (IOException e) {
            System.out.println("连接失败: " + e.getMessage());
            return false;
        }
    }

    private static void showMainMenu() {
        boolean exit = false;

        while (!exit) {
            System.out.println("\n=== 主菜单 ===");
            System.out.println("1. 移动控制");
            System.out.println("2. 预置位操作");
            System.out.println("3. 位置查询");
            System.out.println("4. 辅助功能测试");
            System.out.println("5. 执行自动化测试");
            System.out.println("0. 退出程序");

            System.out.print("\n请选择操作: ");
            int choice = readIntInput();

            switch (choice) {
                case 1:
                    showMovementMenu();
                    break;
                case 2:
                    showPresetMenu();
                    break;
                case 3:
                    showPositionMenu();
                    break;
                case 4:
                    showAuxFunctionMenu();
                    break;
                case 5:
                    runAutomationTest();
                    break;
                case 0:
                    exit = true;
                    break;
                default:
                    System.out.println("无效的选择，请重试。");
            }
        }

        // 断开连接
        System.out.println("正在断开连接...");
        robotSDK.disconnect();
        System.out.println("程序已退出。");
    }

    private static void showMovementMenu() {
        boolean back = false;

        while (!back) {
            System.out.println("\n=== 移动控制菜单 ===");
            System.out.println("1. 向左移动");
            System.out.println("2. 向右移动");
            System.out.println("3. 向上移动");
            System.out.println("4. 向下移动");
            System.out.println("5. 停止移动");
            System.out.println("6. 设置高速模式");
            System.out.println("7. 设置普通速度模式");
            System.out.println("0. 返回主菜单");

            System.out.print("\n请选择操作: ");
            int choice = readIntInput();

            try {
                switch (choice) {
                    case 1:
                        robotSDK.move(InspectRobot.Direction.Left, true);
                        System.out.println("正在向左移动...");
                        waitForKeyPress("按任意键停止");
                        robotSDK.move(InspectRobot.Direction.Left, false);
                        break;
                    case 2:
                        robotSDK.move(InspectRobot.Direction.Right, true);
                        System.out.println("正在向右移动...");
                        waitForKeyPress("按任意键停止");
                        robotSDK.move(InspectRobot.Direction.Right, false);
                        break;
                    case 3:
                        robotSDK.move(InspectRobot.Direction.Up, true);
                        System.out.println("正在向上移动...");
                        waitForKeyPress("按任意键停止");
                        robotSDK.move(InspectRobot.Direction.Up, false);
                        break;
                    case 4:
                        robotSDK.move(InspectRobot.Direction.Down, true);
                        System.out.println("正在向下移动...");
                        waitForKeyPress("按任意键停止");
                        robotSDK.move(InspectRobot.Direction.Down, false);
                        break;
                    case 5:
                        stopAllMovement();
                        System.out.println("已停止所有移动。");
                        break;
                    case 6:
                        robotSDK.setHighSpeed(true);
                        System.out.println("已设置为高速模式。");
                        break;
                    case 7:
                        robotSDK.setHighSpeed(false);
                        System.out.println("已设置为普通速度模式。");
                        break;
                    case 0:
                        back = true;
                        break;
                    default:
                        System.out.println("无效的选择，请重试。");
                }
            } catch (IOException e) {
                System.out.println("操作失败: " + e.getMessage());
            }
        }
    }

    private static void showPresetMenu() {
        boolean back = false;

        while (!back) {
            System.out.println("\n=== 预置位操作菜单 ===");
            System.out.println("1. 设置预置位");
            System.out.println("2. 调用预置位");
            System.out.println("0. 返回主菜单");

            System.out.print("\n请选择操作: ");
            int choice = readIntInput();

            if (choice == 0) {
                back = true;
                continue;
            }

            System.out.print("请输入预置位编号 (1-255): ");
            int presetNumber = readIntInput();
            if (presetNumber < 1 || presetNumber > 255) {
                System.out.println("预置位编号无效，有效范围: 1-255");
                continue;
            }

            try {
                switch (choice) {
                    case 1:
                        robotSDK.setPreset((byte) presetNumber);
                        System.out.println("预置位 #" + presetNumber + " 设置成功。");
                        break;
                    case 2:
                        robotSDK.gotoPreset((byte) presetNumber);
                        System.out.println("正在调用预置位 #" + presetNumber + "...");
                        break;
                    default:
                        System.out.println("无效的选择，请重试。");
                }
            } catch (IOException e) {
                System.out.println("操作失败: " + e.getMessage());
            }
        }
    }

    private static void showPositionMenu() {
        boolean back = false;

        while (!back) {
            System.out.println("\n=== 位置查询菜单 ===");
            System.out.println("1. 查询水平位置");
            System.out.println("2. 查询垂直位置");
            System.out.println("3. 查询全部位置");
            System.out.println("0. 返回主菜单");

            System.out.print("\n请选择操作: ");
            int choice = readIntInput();

            try {
                switch (choice) {
                    case 1:
                        robotSDK.queryHorizontalPosition();
                        System.out.println("正在查询水平位置...");
                        sleepMillis(500); // 给一些时间接收回应
                        break;
                    case 2:
                        robotSDK.queryVerticalPosition();
                        System.out.println("正在查询垂直位置...");
                        sleepMillis(500); // 给一些时间接收回应
                        break;
                    case 3:
                        robotSDK.queryHorizontalPosition();
                        System.out.println("正在查询水平位置...");
                        sleepMillis(500);

                        robotSDK.queryVerticalPosition();
                        System.out.println("正在查询垂直位置...");
                        sleepMillis(500);
                        break;
                    case 0:
                        back = true;
                        break;
                    default:
                        System.out.println("无效的选择，请重试。");
                }
            } catch (IOException e) {
                System.out.println("操作失败: " + e.getMessage());
            }
        }
    }

    private static void showAuxFunctionMenu() {
        boolean back = false;

        while (!back) {
            System.out.println("\n=== 辅助功能菜单 ===");
            System.out.println("1. 打开灯光");
            System.out.println("2. 关闭灯光");
            System.out.println("3. 辅助功能控制");
            System.out.println("0. 返回主菜单");

            System.out.print("\n请选择操作: ");
            int choice = readIntInput();

            try {
                switch (choice) {
                    case 1:
                        robotSDK.setLight(true);
                        System.out.println("灯光已打开。");
                        break;
                    case 2:
                        robotSDK.setLight(false);
                        System.out.println("灯光已关闭。");
                        break;
                    case 3:
                        System.out.print("请输入功能码 (1-255): ");
                        int functionCode = readIntInput();
                        if (functionCode < 1 || functionCode > 255) {
                            System.out.println("功能码无效，有效范围: 1-255");
                            continue;
                        }

                        System.out.print("启用(1)或禁用(0): ");
                        int enableChoice = readIntInput();
                        boolean enable = enableChoice == 1;

                        robotSDK.auxControl((byte) functionCode, enable);
                        System.out.println("功能码 " + functionCode + " 已" +
                                (enable ? "启用" : "禁用") + "。");
                        break;
                    case 0:
                        back = true;
                        break;
                    default:
                        System.out.println("无效的选择，请重试。");
                }
            } catch (IOException e) {
                System.out.println("操作失败: " + e.getMessage());
            }
        }
    }

    private static void runAutomationTest() {
        System.out.println("\n=== 执行自动化测试 ===");
        System.out.println("该测试将按顺序执行以下操作:");
        System.out.println("1. 检查灯光控制");
        System.out.println("2. 测试移动功能 (左右上下)");
        System.out.println("3. 测试预置位设置和调用");
        System.out.println("4. 测试位置查询");

        System.out.print("\n是否开始自动化测试? (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();

        if (!confirm.equals("y")) {
            System.out.println("已取消自动化测试。");
            return;
        }

        final CountDownLatch testCompleteLatch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                // 1. 灯光测试
                System.out.println("\n[自动化测试] 开始灯光测试...");
                robotSDK.setLight(true);
                System.out.println("[自动化测试] 灯光已打开");
                sleepMillis(1000);

                robotSDK.setLight(false);
                System.out.println("[自动化测试] 灯光已关闭");
                sleepMillis(1000);

                // 2. 移动测试
                System.out.println("\n[自动化测试] 开始移动测试...");

                // 左移
                System.out.println("[自动化测试] 开始左移测试 (2秒)");
                robotSDK.move(InspectRobot.Direction.Left, true);
                sleepMillis(2000);
                robotSDK.move(InspectRobot.Direction.Left, false);
                sleepMillis(500);

                // 右移
                System.out.println("[自动化测试] 开始右移测试 (2秒)");
                robotSDK.move(InspectRobot.Direction.Right, true);
                sleepMillis(2000);
                robotSDK.move(InspectRobot.Direction.Right, false);
                sleepMillis(500);

                // 上移
                System.out.println("[自动化测试] 开始上移测试 (2秒)");
                robotSDK.move(InspectRobot.Direction.Up, true);
                sleepMillis(2000);
                robotSDK.move(InspectRobot.Direction.Up, false);
                sleepMillis(500);

                // 下移
                System.out.println("[自动化测试] 开始下移测试 (2秒)");
                robotSDK.move(InspectRobot.Direction.Down, true);
                sleepMillis(2000);
                robotSDK.move(InspectRobot.Direction.Down, false);
                sleepMillis(500);

                // 3. 预置位测试
                System.out.println("\n[自动化测试] 开始预置位测试...");
                System.out.println("[自动化测试] 设置预置位 #1");
                robotSDK.setPreset((byte) 1);
                sleepMillis(1000);

                System.out.println("[自动化测试] 移动一段距离...");
                robotSDK.move(InspectRobot.Direction.Right, true);
                sleepMillis(3000);
                robotSDK.move(InspectRobot.Direction.Right, false);
                sleepMillis(500);

                System.out.println("[自动化测试] 调用预置位 #1");
                robotSDK.gotoPreset((byte) 1);
                sleepMillis(5000); // 等待返回预置位

                // 4. 位置查询测试
                System.out.println("\n[自动化测试] 开始位置查询测试...");
                System.out.println("[自动化测试] 查询水平位置");
                robotSDK.queryHorizontalPosition();
                sleepMillis(1000);

                System.out.println("[自动化测试] 查询垂直位置");
                robotSDK.queryVerticalPosition();
                sleepMillis(1000);

                System.out.println("\n[自动化测试] 测试完成!");

            } catch (IOException e) {
                System.out.println("[自动化测试] 测试失败: " + e.getMessage());
            } finally {
                testCompleteLatch.countDown();
            }
        }).start();

        try {
            // 等待测试完成或用户中断
            System.out.println("测试正在执行中... 按Enter键中断测试");

            new Thread(() -> {
                scanner.nextLine();
                if (testCompleteLatch.getCount() > 0) {
                    System.out.println("\n[自动化测试] 用户中断，正在停止...");
                    try {
                        stopAllMovement();
                    } catch (IOException e) {
                        System.out.println("[自动化测试] 停止移动失败: " + e.getMessage());
                    }
                    testCompleteLatch.countDown();
                }
            }).start();

            testCompleteLatch.await();

        } catch (InterruptedException e) {
            System.out.println("[自动化测试] 被中断: " + e.getMessage());
        }
    }

    private static void stopAllMovement() throws IOException {
        // 停止所有方向的移动
        robotSDK.move(InspectRobot.Direction.Left, false);
        robotSDK.move(InspectRobot.Direction.Right, false);
        robotSDK.move(InspectRobot.Direction.Up, false);
        robotSDK.move(InspectRobot.Direction.Down, false);
    }

    private static void waitForKeyPress(String message) {
        System.out.println(message);
        scanner.nextLine();
    }

    private static int readIntInput() {
        try {
            String input = scanner.nextLine().trim();
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1; // 无效输入
        }
    }

    private static void sleepMillis(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
