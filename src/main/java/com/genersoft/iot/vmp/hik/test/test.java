package com.genersoft.iot.vmp.hik.test;

import com.sun.jna.Structure;

public class test {

    static public class UserData extends Structure {
        static public Integer x;
        public Integer lPort;
        public int i;

        public UserData() {
            x = 600;
            lPort = 100;
            i = 200;
        }

//        @Override
//        protected List<String> getFieldOrder() {
//            return Arrays.asList("x", "lPort", "i");
//        }
    }

    public static void main(String[] args) {
        UserData userData = new UserData();
        userData.x = 800;
        userData.lPort = 1;
        userData.i = 2;

        System.out.println(UserData.x);

        UserData userData2 = new UserData();
        userData2.lPort = 3;
        userData2.i = 4;
        userData2.x = 900;

        System.out.println(userData2.x);
        System.out.println(userData.x);

        UserData.x = 10000000;

        System.out.println(UserData.x);

        if (userData.getPointer() == userData2.getPointer()){
            System.out.println("相同");
        } else {
            System.out.println("不同");
        }
    }
}
