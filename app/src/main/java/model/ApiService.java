package model;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    // Sử dụng model Gemini 1.5 Flash (Nhanh và miễn phí tốt nhất hiện nay)
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    Call<ChatResponse> sendMessage(
            @Query("key") String apiKey, // Truyền key vào URL ?key=...
            @Body ChatRequest request
    );
}