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
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "messages";

    private static final String COL_ID = "msg_id";
    private static final String COL_CONTENT = "content";
    private static final String COL_IS_USER = "is_user";
    private static final String COL_TIME = "timestamp";
    private static final String COL_SESSION_ID = "session_id"; // Để phân biệt các đoạn chat

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tạo bảng thông thường (FTS4 khó hỗ trợ nhiều session query phức tạp nên dùng bảng thường có Index cho an toàn)
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY, " +
                COL_CONTENT + " TEXT, " +
                COL_IS_USER + " INTEGER, " +
                COL_TIME + " TEXT, " +
                COL_SESSION_ID + " TEXT)";
        db.execSQL(createTable);

        // Index giúp tìm kiếm theo session nhanh hơn
        db.execSQL("CREATE INDEX idx_session ON " + TABLE_NAME + " (" + COL_SESSION_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void addMessage(Message message, String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ID, message.getId());
        values.put(COL_CONTENT, message.getText());
        values.put(COL_IS_USER, message.isSentByUser() ? 1 : 0);
        values.put(COL_TIME, message.getTimestamp());
        values.put(COL_SESSION_ID, sessionId);
        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    // Lấy tin nhắn theo Session và phân trang (Load More)
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
                list.add(new Message(id, content, isUser == 1, time));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        Collections.reverse(list);
        return list;
    }
}