package com.example.chatbox;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private List<Message> currentChat = new ArrayList<>();
    private Map<String, List<Message>> chatHistory = new LinkedHashMap<>();
    private String currentChatId = null;
    private int chatCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                onBackPressedCallback.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                onBackPressedCallback.setEnabled(false);
            }
        };
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        recyclerView = findViewById(R.id.recyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        welcomeText = findViewById(R.id.welcome_text);

        messageAdapter = new MessageAdapter(currentChat);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);

        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String messageTextStr = messageEditText.getText().toString().trim();
        if (messageTextStr.isEmpty()) {
            return;
        }

        if (welcomeText.getVisibility() == View.VISIBLE) {
            welcomeText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (currentChatId == null) {
            startNewChatSession(messageTextStr);
        }

        currentChat.add(new Message(messageTextStr, true));
        messageAdapter.notifyItemInserted(currentChat.size() - 1);

        // Simulate a response from the bot
        currentChat.add(new Message("Đây là câu trả lời mẫu.", false));
        messageAdapter.notifyItemInserted(currentChat.size() - 1);

        recyclerView.scrollToPosition(currentChat.size() - 1);
        messageEditText.setText("");
    }

    private void saveCurrentChat() {
        if (currentChatId != null && !currentChat.isEmpty()) {
            chatHistory.put(currentChatId, new ArrayList<>(currentChat));
        }
    }

    private void startNewChatSession(String firstMessage) {
        saveCurrentChat();
        currentChat.clear();
        messageAdapter.notifyDataSetChanged();

        chatCounter++;
        currentChatId = "chat_" + chatCounter;

        Menu menu = navigationView.getMenu();
        SubMenu historyMenu = menu.findItem(R.id.chat_history_group).getSubMenu();
        String chatTitle = firstMessage.length() > 25 ? firstMessage.substring(0, 25) + "..." : firstMessage;
        historyMenu.add(R.id.chat_history_group, Menu.NONE, chatCounter, chatTitle).setCheckable(true);
    }

    private void loadChat(String chatId) {
        if (chatHistory.containsKey(chatId)) {
            saveCurrentChat();
            currentChatId = chatId;
            currentChat.clear();
            currentChat.addAll(chatHistory.get(chatId));
            messageAdapter.notifyDataSetChanged();

            welcomeText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.scrollToPosition(currentChat.size() - 1);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        int order = item.getOrder();

        if (id == R.id.nav_new_chat) {
            saveCurrentChat();
            currentChat.clear();
            messageAdapter.notifyDataSetChanged();
            currentChatId = null;
            welcomeText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            navigationView.getMenu().findItem(R.id.nav_new_chat).setChecked(true);

        } else if (item.getGroupId() == R.id.chat_history_group) {
            String selectedChatId = "chat_" + order;
            if (!selectedChatId.equals(currentChatId)) {
                loadChat(selectedChatId);
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}
