package com.example.chatbox;

import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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

    private EditText edtSearch;
    private RecyclerView recyclerSearch;
    private long selectedDateStart = 0;
    private long selectedDateEnd = 0;

    // Các thành phần Backend
    private DatabaseHelper dbHelper;
    private ApiService apiService;
    private SnowflakeGenerator idGenerator;

    private long backPressedTime;

    private List<Message> currentChatList = new ArrayList<>();
    private String currentChatId = null;
    private int chatCounter = 0;
    private boolean isLoadingHistory = false; // Cờ kiểm soát load more

    private Queue<String> requestQueue = new LinkedList<>();
    private boolean isProcessing = false;

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
            requestQueue.clear();
            isProcessing = false;
            welcomeText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            messageEditText.setText("");
            int size = navigationView.getMenu().findItem(R.id.chat_history_group).getSubMenu().size();
            for (int i = 0; i < size; i++) {
                navigationView.getMenu().findItem(R.id.chat_history_group).getSubMenu().getItem(i).setChecked(false);
            }

            // 4. Đóng ngăn kéo menu
            drawerLayout.closeDrawer(GravityCompat.START);
        });


        // 1. Khởi tạo Backend
        dbHelper = new DatabaseHelper(this);
        idGenerator = new SnowflakeGenerator();
        apiService = RetrofitClient.getInstance().create(ApiService.class);
        dbHelper.deleteOldSessions(this);


        // 2. Setup UI (Giữ nguyên code UI của bạn)
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        setupRightDrawer();

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
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Ưu tiên 1: Nếu Menu (Drawer) đang mở -> Đóng nó lại
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return; // Dừng lại, không thoát app
                }

                // Ưu tiên 2: Xử lý thoát ứng dụng (Double tap)
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    // Nếu lần bấm này cách lần trước dưới 2 giây (2000ms) -> Thoát thật
                    finish();
                    // Hoặc dùng: System.exit(0); nếu muốn tắt hẳn
                } else {
                    // Nếu đây là lần bấm đầu tiên (hoặc đã quá 2 giây)
                    Toast.makeText(MainActivity.this, "Nhấn thêm lần nữa để thoát", Toast.LENGTH_SHORT).show();

                    // Ghi lại thời gian bấm hiện tại
                    backPressedTime = System.currentTimeMillis();
                }
            }
        });
        updateHistoryMenu();
    }

    private void setupRightDrawer() {
        edtSearch = findViewById(R.id.edtSearchQuery);
        recyclerSearch = findViewById(R.id.recyclerSearchResults);
        recyclerSearch.setLayoutManager(new LinearLayoutManager(this));

        Button btnPickDate = findViewById(R.id.btnPickDate);

        // 1. Xử lý chọn ngày
        btnPickDate.setOnClickListener(v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                // Set thời gian bắt đầu ngày (00:00:00)
                c.set(year, month, dayOfMonth, 0, 0, 0);
                selectedDateStart = c.getTimeInMillis();

                // Set thời gian kết thúc ngày (23:59:59)
                c.set(year, month, dayOfMonth, 23, 59, 59);
                selectedDateEnd = c.getTimeInMillis();

                btnPickDate.setText("Ngày: " + dayOfMonth + "/" + (month + 1) + "/" + year);

                // Tự động tìm kiếm lại nếu đang có text
                performSearch();
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        // 2. Xử lý khi nhấn nút Search trên bàn phím (IME Action)
        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                // Ẩn bàn phím
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        View btnClose = findViewById(R.id.btnCloseSearch);
        btnClose.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            // Có thể thêm: Ẩn bàn phím nếu đang mở
        });
    }

    private void performSearch() {
        if (currentChatId == null) return;

        String keyword = edtSearch.getText().toString().trim();
        List<Message> results = dbHelper.searchMessages(currentChatId, keyword);

        // Lọc theo ngày (nếu có chọn ngày)
        if (selectedDateStart > 0) {
            List<Message> filtered = new ArrayList<>();
            for (Message m : results) {
                long time = Long.parseLong(m.getTimestamp());
                if (time >= selectedDateStart && time <= selectedDateEnd) {
                    filtered.add(m);
                }
            }
            results = filtered;
        }

        // Hiển thị lên RecyclerView trong Drawer phải
        SearchAdapter searchAdapter = new SearchAdapter(results, message -> {
            // Xử lý khi bấm vào kết quả: Jump đến tin nhắn
            jumpToMessage(message);
        });
        recyclerSearch.setAdapter(searchAdapter);
    }

    private void jumpToMessage(Message targetMsg) {
        // 1. Đóng Drawer
        drawerLayout.closeDrawer(GravityCompat.END);

        // 2. Tìm vị trí tin nhắn trong list hiện tại
        int targetPosition = -1;
        for (int i = 0; i < currentChatList.size(); i++) {
            if (currentChatList.get(i).getId() == targetMsg.getId()) {
                targetPosition = i;
                break;
            }
        }

        if (targetPosition != -1) {
            // Nếu tìm thấy: Cuộn tới đó và highlight (nháy nhẹ) nếu muốn
            recyclerView.smoothScrollToPosition(targetPosition);
            // Có thể thêm hiệu ứng nháy màu nền item tại position này (nâng cao)
        } else {
            // Nếu không tìm thấy (do phân trang chưa load tới):
            // Bạn cần load lại toàn bộ lịch sử hoặc load đến điểm đó.
            // Để đơn giản: Load lại toàn bộ
            Toast.makeText(this, "Đang tải tin nhắn...", Toast.LENGTH_SHORT).show();
            // (Thực tế bạn nên implement logic loadMore cho đến khi thấy ID, nhưng tạm thời thông báo vậy)
        }
    }

    private void sendMessage() {
        String text = messageEditText.getText().toString().trim();
        if (text.isEmpty()) return;

        if (welcomeText.getVisibility() == View.VISIBLE) {
            welcomeText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (currentChatId == null) {
            startNewChatSession(text);
        }

        // --- BƯỚC A: LUÔN HIỆN TIN NHẮN NGƯỜI DÙNG NGAY LẬP TỨC ---
        long userId = idGenerator.nextId();
        Message userMsg = new Message(userId, text, true, String.valueOf(System.currentTimeMillis()));
        dbHelper.addMessage(userMsg, currentChatId);
        currentChatList.add(userMsg);
        messageAdapter.notifyItemInserted(currentChatList.size() - 1);
        recyclerView.smoothScrollToPosition(currentChatList.size() - 1);

        messageEditText.setText(""); // Xóa ô nhập ngay

        // --- BƯỚC B: THÊM VÀO HÀNG ĐỢI VÀ XỬ LÝ ---
        requestQueue.add(text); // Đẩy yêu cầu vào hàng đợi
        processNextRequest();   // Thử xử lý
    }

    private void processNextRequest() {
        // Nếu đang bận hoặc hàng đợi rỗng thì thôi
        if (isProcessing || requestQueue.isEmpty()) {
            return;
        }

        isProcessing = true; // Đánh dấu là đang bận
        String textToProcess = requestQueue.peek(); // Lấy tin nhắn đầu hàng đợi (chưa xóa)

        // Hiện loading
        messageAdapter.addLoading();
        recyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);

        // Gọi API
        ChatRequest request = new ChatRequest(textToProcess);
        String apiKey = BuildConfig.GEMINI_API_KEY;

        // Chụp lại ID hiện tại để handle callback đúng chỗ
        final String sendingChatId = currentChatId;

        apiService.sendMessage(apiKey, request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                // Xử lý xong 1 request
                handleApiFinish(sendingChatId, response, null);
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                handleApiFinish(sendingChatId, null, t);
            }
        });
    }

    private void handleApiFinish(String chatId, Response<ChatResponse> response, Throwable t) {
        // 1. Luôn xóa loading trước
        if (isCurrentChat(chatId)) {
            messageAdapter.removeLoading();
        }

        // 2. Lấy nội dung câu hỏi vừa xử lý ra khỏi hàng đợi
        String questionText = requestQueue.poll();

        // 3. Lấy nội dung trả lời
        String botReplyText;
        if (t != null) {
            botReplyText = "Lỗi kết nối: " + t.getMessage();
        } else if (response != null && response.isSuccessful() && response.body() != null) {
            botReplyText = response.body().getReplyText();
        } else {
            botReplyText = "Lỗi API: " + (response != null ? response.code() : "Unknown");
        }

        // 4. Tạo tin nhắn Bot (CÓ KÈM replyTo là câu hỏi gốc)
        long botId = idGenerator.nextId();
        // Truyền questionText vào làm nội dung trích dẫn
        Message botMsg = new Message(botId, botReplyText, false, String.valueOf(System.currentTimeMillis()), questionText);

        // 5. Lưu và Hiện
        dbHelper.addMessage(botMsg, chatId); // Lưu vào DB (cần sửa hàm addMessage trong DB để lưu cả replyTo nếu muốn, hoặc bỏ qua)

        if (isCurrentChat(chatId)) {
            currentChatList.add(botMsg);
            messageAdapter.notifyItemInserted(currentChatList.size() - 1);
            recyclerView.smoothScrollToPosition(currentChatList.size() - 1);
        }

        // 6. QUAN TRỌNG: Mở khóa và xử lý tin nhắn tiếp theo trong hàng đợi
        isProcessing = false;

        // Delay nhẹ 500ms để người dùng kịp nhìn thấy bot trả lời xong trước khi nó load tiếp câu sau
        new Handler().postDelayed(this::processNextRequest, 500);
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
        getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .edit()
                .putLong("last_view_" + chatId, System.currentTimeMillis())
                .apply();

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
        if (item.getGroupId() == R.id.chat_history_group) {
            // Lấy lại Session ID đã giấu lúc nãy
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                CharSequence description = item.getContentDescription();
                if (description != null) {
                    String sessionId = description.toString();
                    loadChat(sessionId);

                    // Reset UI
                    welcomeText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    messageEditText.setText("");

                    // 3. QUAN TRỌNG: Đánh dấu item này là đang chọn
                    item.setChecked(true);
                }
            }
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
            // Mở ngăn kéo phải
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END);
            } else {
                drawerLayout.openDrawer(GravityCompat.END);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    // Hàm này sẽ xóa menu cũ và load lại danh sách từ DB
    private void updateHistoryMenu() {
        Menu menu = navigationView.getMenu();
        MenuItem item = menu.findItem(R.id.chat_history_group);
        SubMenu subMenu = item.getSubMenu();

        subMenu.clear();

        List<DatabaseHelper.ChatSession> sessions = dbHelper.getAllSessions();

        // Cần set group này là single choice để chỉ 1 item được chọn tại 1 thời điểm
        subMenu.setGroupCheckable(R.id.chat_history_group, true, true);

        for (int i = 0; i < sessions.size(); i++) {
            DatabaseHelper.ChatSession session = sessions.get(i);

            // Thêm item vào group R.id.chat_history_group
            MenuItem newItem = subMenu.add(R.id.chat_history_group, i, i, session.title);
            newItem.setIcon(android.R.drawable.ic_menu_edit);

            // 1. QUAN TRỌNG: Cho phép item này được "Check"
            newItem.setCheckable(true);

            // 2. Nếu đây là đoạn chat đang mở, hãy đánh dấu nó ngay lập tức
            if (currentChatId != null && currentChatId.equals(session.sessionId)) {
                newItem.setChecked(true);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                newItem.setContentDescription(session.sessionId);
            }
            ImageButton btnDelete = new ImageButton(this);
            btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
            btnDelete.setBackgroundColor(android.graphics.Color.TRANSPARENT); // Nền trong suốt
            btnDelete.setPadding(16, 0, 16, 0);

            // Xử lý khi bấm vào thùng rác
            btnDelete.setOnClickListener(v -> {
                // Đóng menu lại cho gọn
                drawerLayout.closeDrawer(GravityCompat.START);
                // Hiện Popup xác nhận
                showDeletePopup(session);
            });
            newItem.setActionView(btnDelete);
        }
    }

    private void showDeletePopup(DatabaseHelper.ChatSession session) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        // Nạp layout popup vừa tạo
        View view = getLayoutInflater().inflate(R.layout.layout_delete_popup, null);
        bottomSheetDialog.setContentView(view);

        // Xử lý nút bấm trong Popup
        view.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            bottomSheetDialog.dismiss(); // Tắt popup
            showConfirmDialog(session);  // Hiện Dialog xác nhận lần cuối
        });

        bottomSheetDialog.show();
    }

    private void showConfirmDialog(DatabaseHelper.ChatSession session) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cảnh báo")
                .setMessage("Bạn có chắc chắn muốn xóa vĩnh viễn đoạn chat: \"" + session.title + "\" không?")
                .setPositiveButton("Xóa ngay", (dialog, which) -> {
                    // 1. Gọi DB xóa
                    dbHelper.deleteSession(session.sessionId);

                    // 2. Nếu đang xem đoạn chat bị xóa thì reset màn hình
                    if (currentChatId != null && currentChatId.equals(session.sessionId)) {
                        currentChatList.clear();
                        messageAdapter.notifyDataSetChanged();
                        currentChatId = null;
                        welcomeText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }

                    // 3. Cập nhật lại menu
                    updateHistoryMenu();

                    Toast.makeText(this, "Đã xóa thành công!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}