package com.example.chatbox;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import model.Message;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "ChatHistory.db";

    // 1. TĂNG VERSION LÊN 2 ĐỂ CẬP NHẬT CẤU TRÚC BẢNG
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_NAME = "messages";

    private static final String COL_ID = "msg_id";
    private static final String COL_CONTENT = "content";
    private static final String COL_IS_USER = "is_user";
    private static final String COL_TIME = "timestamp";
    private static final String COL_SESSION_ID = "session_id";

    // 2. KHAI BÁO CỘT MỚI
    private static final String COL_REPLY_TO = "reply_to";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 3. THÊM CỘT COL_REPLY_TO VÀO CÂU LỆNH TẠO BẢNG
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY, " +
                COL_CONTENT + " TEXT, " +
                COL_IS_USER + " INTEGER, " +
                COL_TIME + " TEXT, " +
                COL_SESSION_ID + " TEXT, " +
                COL_REPLY_TO + " TEXT)"; // <-- Thêm ở đây
        db.execSQL(createTable);

        db.execSQL("CREATE INDEX idx_session ON " + TABLE_NAME + " (" + COL_SESSION_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Version 1 lên 2: Thêm cột reply_to
            // Dùng ALTER TABLE để thêm cột mà không xóa dữ liệu
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_REPLY_TO + " TEXT");
            } catch (Exception e) {
                // Đôi khi cột đã tồn tại (do debug), ta cứ kệ nó
            }
        }
    }

    public void addMessage(Message message, String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ID, message.getId());
        values.put(COL_CONTENT, message.getText());
        values.put(COL_IS_USER, message.isSentByUser() ? 1 : 0);
        values.put(COL_TIME, message.getTimestamp());
        values.put(COL_SESSION_ID, sessionId);

        // 4. LƯU THÊM TRƯỜNG REPLY_TO
        values.put(COL_REPLY_TO, message.getReplyTo());

        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    public List<Message> getMessagesBefore(String sessionId, long lastId, int limit) {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COL_SESSION_ID + " = ? AND " + COL_ID + " < ?" +
                " ORDER BY " + COL_ID + " DESC LIMIT ?";

        Cursor cursor = db.rawQuery(sql, new String[]{sessionId, String.valueOf(lastId), String.valueOf(limit)});

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT));
                int isUser = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_USER));
                String time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));

                // 5. LẤY DỮ LIỆU REPLY TỪ DATABASE RA
                String replyTo = null;
                // Kiểm tra xem cột có tồn tại không (phòng hờ)
                int replyIndex = cursor.getColumnIndex(COL_REPLY_TO);
                if (replyIndex != -1) {
                    replyTo = cursor.getString(replyIndex);
                }

                // Dùng Constructor đầy đủ (có replyTo)
                list.add(new Message(id, content, isUser == 1, time, replyTo));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        Collections.reverse(list);
        return list;
    }

    // ... Phần ChatSession và getAllSessions giữ nguyên ...
    public static class ChatSession {
        public String sessionId;
        public String title;

        public ChatSession(String sessionId, String title) {
            this.sessionId = sessionId;
            this.title = title;
        }
    }

    public List<ChatSession> getAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + COL_SESSION_ID + ", " + COL_CONTENT +
                " FROM " + TABLE_NAME +
                " GROUP BY " + COL_SESSION_ID +
                " ORDER BY " + COL_TIME + " DESC";

        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                String id = cursor.getString(0);
                String content = cursor.getString(1);
                String title = content.length() > 30 ? content.substring(0, 27) + "..." : content;
                sessions.add(new ChatSession(id, title));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return sessions;
    }
    public void deleteSession(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Xóa tất cả tin nhắn thuộc session_id đó
        db.delete(TABLE_NAME, COL_SESSION_ID + " = ?", new String[]{sessionId});
        db.close();
    }

    // 2. Hàm tự động dọn dẹp tin nhắn cũ hơn 7 ngày
    // Cần truyền Context để truy cập SharedPreferences
    public void deleteOldSessions(Context context) {
        SQLiteDatabase db = this.getWritableDatabase();
        long oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);

        // 1. Lấy tất cả session_id và thời gian tin nhắn mới nhất của nó (MAX timestamp)
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_SESSION_ID + ", MAX(" + COL_TIME + ") as last_msg_time " +
                        "FROM " + TABLE_NAME + " GROUP BY " + COL_SESSION_ID, null);

        if (cursor.moveToFirst()) {
            android.content.SharedPreferences prefs =
                    context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);

            do {
                String sessionId = cursor.getString(0);
                String lastMsgTimeStr = cursor.getString(1);

                // Lấy thời gian mở xem lần cuối từ Prefs
                // Nếu chưa mở lần nào thì lấy thời gian tin nhắn cuối cùng (lastMsgTimeStr) làm mốc
                long lastViewTime = prefs.getLong("last_view_" + sessionId, 0);

                long lastActiveTime;
                if (lastViewTime > 0) {
                    lastActiveTime = lastViewTime; // Ưu tiên thời gian mở xem
                } else {
                    lastActiveTime = Long.parseLong(lastMsgTimeStr); // Fallback về thời gian nhắn
                }

                // 2. Kiểm tra: Nếu lần cuối đụng vào cũ hơn 1 tuần -> XÓA
                if (lastActiveTime < oneWeekAgo) {
                    db.delete(TABLE_NAME, COL_SESSION_ID + " = ?", new String[]{sessionId});

                    // Xóa luôn trong Prefs cho sạch
                    prefs.edit().remove("last_view_" + sessionId).apply();
                }

            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
    }
    public List<Message> searchMessages(String sessionId, String keyword) {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Tìm nội dung chứa từ khóa (LIKE %keyword%)
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COL_SESSION_ID + " = ? AND " + COL_CONTENT + " LIKE ?";

        Cursor cursor = db.rawQuery(sql, new String[]{sessionId, "%" + keyword + "%"});

        if (cursor.moveToFirst()) {
            do {
                // ... (Copy đoạn lấy dữ liệu giống hàm getMessagesBefore) ...
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT));
                int isUser = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_USER));
                String time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));

                // Lấy reply_to nếu có (code cũ của bạn)
                String replyTo = null;
                int replyIndex = cursor.getColumnIndex("reply_to"); // Hardcode tên cột để tránh lỗi nếu chưa có biến static
                if (replyIndex != -1) replyTo = cursor.getString(replyIndex);

                list.add(new Message(id, content, isUser == 1, time, replyTo));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }
}