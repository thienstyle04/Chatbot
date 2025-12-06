package model;

import java.util.List;

public class ChatResponse {
    private List<Candidate> candidates;

    // Class con lấy dữ liệu trả về
    public static class Candidate {
        public ChatRequest.Content content;
    }

    // Hàm tiện ích để lấy nhanh câu trả lời
    public String getReplyText() {
        if (candidates != null && !candidates.isEmpty()) {
            Candidate firstCandidate = candidates.get(0);
            if (firstCandidate.content != null &&
                    firstCandidate.content.parts != null &&
                    !firstCandidate.content.parts.isEmpty()) {
                return firstCandidate.content.parts.get(0).text;
            }
        }
        return "Gemini không trả lời hoặc lỗi cấu trúc.";
    }
}