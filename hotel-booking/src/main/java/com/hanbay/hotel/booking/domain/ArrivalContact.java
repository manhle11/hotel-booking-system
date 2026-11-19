package com.hanbay.hotel.booking.domain;

public final class ArrivalContact {
    private ArrivalContact() {}

    public enum Channel {
        PHONE("Điện thoại"),
        EMAIL("Email"),
        MESSAGE("Tin nhắn"),
        IN_PERSON("Trực tiếp"),
        OTHER("Khác");
        private final String label;

        Channel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Outcome {
        STILL_COMING("Khách vẫn đến"),
        NO_ANSWER("Không nghe máy / chưa phản hồi"),
        CANCELLATION("Khách báo hủy"),
        RESCHEDULE("Khách xin đổi lịch"),
        UNDECIDED("Chưa xác định");
        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
