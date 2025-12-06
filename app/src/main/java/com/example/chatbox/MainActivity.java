package com.example.chatbox;

import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import model.ApiService;
import model.ChatRequest;
import model.ChatResponse;
import model.Message;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private Toolbar toolbar;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;

    private RecyclerView recyclerView;
    private MessageAdapter messageAdapter;
    private EditText messageEditText;
    private ImageButton sendButton;
    private TextView welcomeText;

    // Các thành phần Backend
    private DatabaseHelper dbHelper;
    private ApiService apiService;
    private SnowflakeGenerator idGenerator;

    private List<Message> currentChatList = new ArrayList<>();
    private String currentChatId = null;
    private int chatCounter = 0;
    private boolean isLoadingHistory = false; // Cờ kiểm soát load more

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View btnNewChat = findViewById(R.id.btnNewChatFixed);
        btnNewChat.setOnClickListener(v -> {
            // Copy logic cũ của R.id.nav_new_chat vào đây
            currentChatList.clear();
            messageAdapter.notifyDataSetChanged();
            currentChatId = null;
            welcomeText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            messageEditText.setText("");

            // 4. Đóng ngăn kéo menu
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 1. Khởi tạo Backend
        dbHelper = new DatabaseHelper(this);
        idGenerator = new SnowflakeGenerator();
        apiService = RetrofitClient.getInstance().create(ApiService.class);

        // 2. Setup UI (Giữ nguyên code UI của bạn)
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // ... (Phần OnBackPressedCallback và Toggle giữ nguyên) ...
        final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() { drawerLayout.closeDrawer(GravityCompat.START); }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        recyclerView = findViewById(R.id.recyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        welcomeText = findViewById(R.id.welcome_text);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter(currentChatList);
        recyclerView.setAdapter(messageAdapter);

        // 3. Xử lý sự kiện gửi tin
        sendButton.setOnClickListener(v -> sendMessage());

        // 4. Xử lý sự kiện cuộn để Load More (Pagination)
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (currentChatId != null && !isLoadingHistory && layoutManager.findFirstCompletelyVisibleItemPosition() == 0) {
                    loadMoreMessages();
                }
            }
        });
    }

    private void sendMessage() {
        String text = messageEditText.getText().toString().trim();
        if (text.isEmpty()) return;

        // Xử lý giao diện Welcome
        if (welcomeText.getVisibility() == View.VISIBLE) {
            welcomeText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        // Tạo session mới nếu chưa có
        if (currentChatId == null) {
            startNewChatSession(text);
        }

        // 1. "CHỤP LẠI" ID CỦA ĐOẠN CHAT HIỆN TẠI (Quan trọng nhất)
        // Biến này sẽ được giữ cố định cho riêng lần gọi API này
        final String sendingChatId = currentChatId;

        // 2. Tạo tin nhắn User và hiện lên UI
        long userId = idGenerator.nextId();
        Message userMsg = new Message(userId, text, true, String.valueOf(System.currentTimeMillis()));

        // Lưu và hiện
        dbHelper.addMessage(userMsg, sendingChatId);
        currentChatList.add(userMsg);
        messageAdapter.notifyItemInserted(currentChatList.size() - 1);
        recyclerView.smoothScrollToPosition(currentChatList.size() - 1);

        // Hiện loading
        messageAdapter.addLoading();
        recyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);

        // Xóa ô nhập liệu
        messageEditText.setText("");

        // 3. Gọi API
        ChatRequest request = new ChatRequest(text);
        // Lưu ý: Thay API Key thật của bạn vào đây
        String apiKey = BuildConfig.GEMINI_API_KEY;

        apiService.sendMessage(apiKey, request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                // KIỂM TRA: Người dùng còn đang ở đoạn chat cũ không?
                if (isCurrentChat(sendingChatId)) {
                    // Nếu ĐÚNG: Xóa loading trên màn hình
                    messageAdapter.removeLoading();
                }

                if (response.isSuccessful() && response.body() != null) {
                    String reply = response.body().getReplyText();
                    // Gọi hàm xử lý phản hồi với ID đã chụp lại
                    handleBotReply(sendingChatId, reply);
                } else {
                    if (isCurrentChat(sendingChatId)) {
                        handleBotReply(sendingChatId, "Lỗi API: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                if (isCurrentChat(sendingChatId)) {
                    messageAdapter.removeLoading();
                    handleBotReply(sendingChatId, "Lỗi kết nối: " + t.getMessage());
                }
            }
        });
    }

    // Đổi tên hàm receiveBotReply cũ thành handleBotReply và thêm tham số chatId
    private void handleBotReply(String targetChatId, String text) {
        long botId = idGenerator.nextId();
        Message botMsg = new Message(botId, text, false, String.valueOf(System.currentTimeMillis()));

        // 1. LUÔN LUÔN Lưu vào Database (vào đúng đoạn chat targetChatId)
        dbHelper.addMessage(botMsg, targetChatId);

        // 2. CHỈ Cập nhật giao diện NẾU người dùng đang xem đúng đoạn chat đó
        if (isCurrentChat(targetChatId)) {
            currentChatList.add(botMsg);
            messageAdapter.notifyItemInserted(currentChatList.size() - 1);
            recyclerView.smoothScrollToPosition(currentChatList.size() - 1);
        } else {
            // Nếu người dùng đã sang đoạn chat khác:
            // Chỉ lưu DB (đã làm ở bước 1) và không làm gì cả.
            // (Tuỳ chọn) Bạn có thể hiện thông báo Toast nhỏ: "Đoạn chat cũ đã có tin nhắn mới"
        }
    }

    // Hàm phụ trợ: Kiểm tra xem ID gửi đi có khớp với ID đang mở không
    private boolean isCurrentChat(String sendingChatId) {
        return currentChatId != null && currentChatId.equals(sendingChatId);
    }

    private void receiveBotReply(String text) {
        long botId = idGenerator.nextId();
        Message botMsg = new Message(botId, text, false, String.valueOf(System.currentTimeMillis()));

        dbHelper.addMessage(botMsg, currentChatId);
        currentChatList.add(botMsg);
        messageAdapter.notifyItemInserted(currentChatList.size() - 1);
        recyclerView.smoothScrollToPosition(currentChatList.size() - 1);
    }

    private void startNewChatSession(String firstMessage) {
        chatCounter++;
        currentChatId = "chat_" + System.currentTimeMillis(); // Dùng timestamp để ID không trùng

        // Thêm vào menu Drawer
        Menu menu = navigationView.getMenu();
        SubMenu historyMenu = menu.findItem(R.id.chat_history_group).getSubMenu();
        String title = firstMessage.length() > 20 ? firstMessage.substring(0, 20) + "..." : firstMessage;

        // Lưu ý: Dùng ID là chatCounter để bắt sự kiện click, nhưng lưu DB bằng currentChatId chuỗi
        historyMenu.add(R.id.chat_history_group, chatCounter, Menu.NONE, title).setCheckable(true);
    }

    // Load 20 tin nhắn mới nhất khi mở đoạn chat cũ
    private void loadChat(String chatId) {
        currentChatId = chatId;
        currentChatList.clear();

        // Lấy 20 tin mới nhất (Id nhỏ hơn MAX_VALUE)
        List<Message> initial = dbHelper.getMessagesBefore(chatId, Long.MAX_VALUE, 20);
        currentChatList.addAll(initial);

        messageAdapter.notifyDataSetChanged();
        welcomeText.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        if (!currentChatList.isEmpty()) {
            recyclerView.scrollToPosition(currentChatList.size() - 1);
        }
    }

    // Load thêm tin nhắn cũ khi cuộn lên
    private void loadMoreMessages() {
        if (currentChatList.isEmpty()) return;

        isLoadingHistory = true;
        long oldestId = currentChatList.get(0).getId();

        // Delay giả lập để thấy hiệu ứng loading (thực tế SQLite rất nhanh)
        new Handler().postDelayed(() -> {
            List<Message> oldMessages = dbHelper.getMessagesBefore(currentChatId, oldestId, 20);
            if (!oldMessages.isEmpty()) {
                messageAdapter.addOldMessages(oldMessages);
            }
            isLoadingHistory = false;
        }, 500);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (item.getGroupId() == R.id.chat_history_group) {
            Toast.makeText(this, "Đã chọn chat cũ (Cần implement bảng Session để lấy ID thực)", Toast.LENGTH_SHORT).show();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
    // 1. Hàm này giúp hiển thị menu lên Toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    // 2. Hàm này xử lý sự kiện khi bấm vào nút trên Toolbar
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            // Viết code xử lý tìm kiếm ở đây
            // Ví dụ: Hiển thị thanh tìm kiếm hoặc mở màn hình tìm kiếm
            android.widget.Toast.makeText(this, "Bạn đã bấm tìm kiếm!", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}