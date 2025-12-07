package model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class ChatRequest {

    // 1. Thêm trường chỉ dẫn hệ thống (System Instruction)
    @SerializedName("system_instruction")
    private Content systemInstruction;

    @SerializedName("contents")
    private List<Content> contents;

    public ChatRequest(String text) {
        // --- PHẦN 1: CÀI ĐẶT MỆNH LỆNH MẶC ĐỊNH ---
        // Đây là nơi bạn dạy Bot cách cư xử ngay từ đầu
        String defaultPrompt = "Bạn là trợ lý ảo AI. Hãy luôn trả lời tôi bằng Tiếng Việt một cách tự nhiên, thân thiện và hữu íchD";

        this.systemInstruction = new Content();
        this.systemInstruction.parts = new ArrayList<>();
        this.systemInstruction.parts.add(new Part(defaultPrompt));

        // --- PHẦN 2: TIN NHẮN NGƯỜI DÙNG ---
        this.contents = new ArrayList<>();
        Content userContent = new Content();
        userContent.parts = new ArrayList<>();
        userContent.parts.add(new Part(text));
        this.contents.add(userContent);
    }

    // Các class con giữ nguyên, chỉ thêm @SerializedName cho chắc chắn
    public static class Content {
        @SerializedName("parts")
        public List<Part> parts;
    }

    public static class Part {
        @SerializedName("text")
        public String text;

        public Part(String text) {
            this.text = text;
        }
    }
}