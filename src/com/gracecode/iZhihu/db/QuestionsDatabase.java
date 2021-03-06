package com.gracecode.iZhihu.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import com.gracecode.iZhihu.R;
import com.gracecode.iZhihu.api.Requester;
import com.gracecode.iZhihu.dao.Question;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

public final class QuestionsDatabase {
    public static final String COLUM_ID = "id";
    public static final String COLUM_QUESTION_ID = "question_id";
    public static final String COLUM_SERVER_ID = "server_id";
    public static final String COLUM_ANSWER_ID = "answer_id";

    public static final String COLUM_QUESTION_TITLE = "question_title";
    public static final String COLUM_CONTENT = "content";
    public static final String COLUM_USER_NAME = "user_name";
    public static final String COLUM_UNREAD = "unread";
    public static final String COLUM_STARED = "stared";
    public static final String COLUM_QUESTION_DESCRIPTION = "question_description";
    public static final String COLUM_UPDATE_AT = "update_at";
    public static final String COLUM_USER_AVATAR = "user_avatar";

    public static final int VALUE_READED = 1;
    public static final int VALUE_UNREADED = 0;
    public static final int VALUE_STARED = 1;
    public static final int VALUE_UNSTARED = 0;

    private static final int DATABASE_VERSION = 1;
    private static final String FILE_DATABASE_NAME = "zhihu.sqlite";
    private static final String DATABASE_QUESTIONS_TABLE_NAME = "izhihu";
    private static final String DATABASE_QUESTIONS_VIRTUAL_TABLE_NAME = "izhihu_";

    private final static String[] SQL_CREATE_TABLES = {
            "CREATE TABLE " + DATABASE_QUESTIONS_TABLE_NAME + " (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT , " +
                    COLUM_ID + " long NOT NULL UNIQUE, " + COLUM_SERVER_ID + " long, " +
                    COLUM_ANSWER_ID + " long, " + COLUM_QUESTION_ID + " long, " +
                    COLUM_USER_NAME + " text,  " + COLUM_USER_AVATAR + " text, " + COLUM_QUESTION_TITLE + " text, " +
                    COLUM_QUESTION_DESCRIPTION + " text, " + COLUM_CONTENT + " text, " + COLUM_UPDATE_AT + " text, " +
                    COLUM_UNREAD + " integer DEFAULT 0, " + COLUM_STARED + " integer DEFAULT 0 );",
            "CREATE VIRTUAL TABLE " + DATABASE_QUESTIONS_VIRTUAL_TABLE_NAME +
                    " USING fts3("
                    + COLUM_ANSWER_ID + " integer not null, "
                    + COLUM_QUESTION_TITLE + " text);",
            "CREATE INDEX " + COLUM_ID + "_idx ON " + DATABASE_QUESTIONS_TABLE_NAME + "(" + COLUM_ID + ");",
            "CREATE INDEX " + COLUM_QUESTION_TITLE + "_idx ON " + DATABASE_QUESTIONS_TABLE_NAME + "(" + COLUM_QUESTION_TITLE + ");",
            "CREATE INDEX " + COLUM_ANSWER_ID + "_idx ON " + DATABASE_QUESTIONS_TABLE_NAME + "(" + COLUM_ANSWER_ID + ");"
    };
    public static final int PRE_LIMIT_PAGE_SIZE = 12;
    public static final int FIRST_PAGE = 1;
    private static final String[] SELECT_ALL = new String[]{
            "_id", COLUM_ID, COLUM_QUESTION_ID, COLUM_ANSWER_ID,
            COLUM_STARED, COLUM_UNREAD,
            COLUM_USER_NAME, COLUM_USER_AVATAR,
            COLUM_UPDATE_AT,
            COLUM_QUESTION_TITLE, COLUM_QUESTION_DESCRIPTION,
            COLUM_CONTENT
    };

    protected final Context context;

    private int idxId;
    private int idxQuestionId;
    private int idxTitle;
    private int idxContent;
    private int idxUserName;
    private int idxDespcrition;
    private int idxStared;
    private int idxUnread;
    private int idxUpdateAt;
    private int idxAnswerId;

    private static final class DatabaseOpenHelper extends SQLiteOpenHelper {
        public DatabaseOpenHelper(Context context) {
            super(context, getDatabaseFilePath(context), null, DATABASE_VERSION);
        }

        public DatabaseOpenHelper(Context context, String name) {
            super(context, name, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            for (String sql : SQL_CREATE_TABLES) {
                sqLiteDatabase.execSQL(sql);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
        }
    }

    public static String getDatabaseFilePath(Context context) {
        return new File(context.getCacheDir(), FILE_DATABASE_NAME).getAbsolutePath();
    }

    public int getStartId() {
        int returnId = Requester.DEFAULT_START_OFFSET;
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT max(" + COLUM_ID + ") AS " +
                COLUM_ID + " FROM " + DATABASE_QUESTIONS_TABLE_NAME + " LIMIT 1;", null);
        cursor.moveToFirst();

        try {
            int maxId = cursor.getInt(cursor.getColumnIndex(COLUM_ID));
            if (cursor.getCount() == 1 && maxId != 0) {
                returnId = maxId;
            }
            return returnId;
        } finally {
            cursor.close();
            db.close();
        }
    }


    public long getTotalQuestionsCount() {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT count(" + COLUM_ID + ") AS " +
                COLUM_ID + " FROM " + DATABASE_QUESTIONS_TABLE_NAME + " LIMIT 1;", null);
        cursor.moveToFirst();

        try {
            return cursor.getLong(cursor.getColumnIndex(COLUM_ID));
        } finally {
            cursor.close();
            db.close();
        }
    }


    public int getTotalPages() {
        return (int) Math.ceil(getTotalQuestionsCount() / PRE_LIMIT_PAGE_SIZE);
    }


    public QuestionsDatabase(Context context) {
        this.context = context;
    }

    public ArrayList<Question> getRecentQuestions() {
        return getRecentQuestions(FIRST_PAGE);
    }


    public ArrayList<Question> getRecentQuestions(int page) {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();
        Cursor cursor = db.query(DATABASE_QUESTIONS_TABLE_NAME, SELECT_ALL,
                null, null, null, null,
                COLUM_UPDATE_AT + " DESC " +
                        " LIMIT " + (page - 1) * PRE_LIMIT_PAGE_SIZE + "," + PRE_LIMIT_PAGE_SIZE);
        try {
            return getAllQuestionsByCursor(cursor);
        } finally {
            cursor.close();
            db.close();
        }
    }


    public ArrayList<Question> searchQuestions(String keys) {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();
        Cursor cursor = db.query(DATABASE_QUESTIONS_TABLE_NAME, SELECT_ALL,
                COLUM_QUESTION_TITLE + " LIKE '%" + keys + "%'", null, null, null,
                COLUM_ANSWER_ID + " LIMIT " + PRE_LIMIT_PAGE_SIZE / 2);
        try {
            return getAllQuestionsByCursor(cursor);
        } finally {
            cursor.close();
            db.close();
        }
    }


    public ArrayList<Question> getStaredQuestions() {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();
        Cursor cursor = db.query(DATABASE_QUESTIONS_TABLE_NAME, SELECT_ALL, " stared = 1 ", null, null, null,
                COLUM_UPDATE_AT + " DESC");
        try {
            return getAllQuestionsByCursor(cursor);
        } finally {
            cursor.close();
            db.close();
        }
    }


    private ArrayList<Question> getAllQuestionsByCursor(Cursor cursor) {
        ArrayList<Question> questionArrayList = new ArrayList<Question>();

        try {
            getIndexFromCursor(cursor);
            for (int i = 0; i < cursor.getCount(); i++) {
                cursor.moveToPosition(i);
                Question question = convertCursorIntoQuestion(cursor);
                questionArrayList.add(question);
            }

            return questionArrayList;
        } finally {
            cursor.close();
        }
    }

//    public Cursor searchQuestionsCursor(String keys) {
//        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();
//        Cursor cursor = db.query(DATABASE_QUESTIONS_VIRTUAL_TABLE_NAME, new String[]{COLUM_ANSWER_ID, COLUM_QUESTION_TITLE},
//                COLUM_QUESTION_TITLE + " MATCH '" + keys + "'", null, null, null,
//                COLUM_ANSWER_ID + " DESC LIMIT " + PRE_LIMIT_PAGE_SIZE / 2);
//        cursor.moveToFirst();
//        return cursor;
//    }

    private void getIndexFromCursor(Cursor cursor) {
        this.idxId = cursor.getColumnIndex(QuestionsDatabase.COLUM_ID);
        this.idxQuestionId = cursor.getColumnIndex(QuestionsDatabase.COLUM_QUESTION_ID);
        this.idxAnswerId = cursor.getColumnIndex(QuestionsDatabase.COLUM_ANSWER_ID);
        this.idxTitle = cursor.getColumnIndex(QuestionsDatabase.COLUM_QUESTION_TITLE);
        this.idxContent = cursor.getColumnIndex(QuestionsDatabase.COLUM_CONTENT);
        this.idxUserName = cursor.getColumnIndex(QuestionsDatabase.COLUM_USER_NAME);
        this.idxDespcrition = cursor.getColumnIndex(QuestionsDatabase.COLUM_QUESTION_DESCRIPTION);
        this.idxStared = cursor.getColumnIndex(QuestionsDatabase.COLUM_STARED);
        this.idxUnread = cursor.getColumnIndex(QuestionsDatabase.COLUM_UNREAD);
        this.idxUpdateAt = cursor.getColumnIndex(QuestionsDatabase.COLUM_UPDATE_AT);
    }

    private Question convertCursorIntoQuestion(Cursor cursor) {
        Question question = new Question();

        question.setId(cursor.getInt(idxId));
        question.setQuestionId(cursor.getInt(idxQuestionId));
        question.setAnswerId(cursor.getInt(idxAnswerId));

        question.setTitle(cursor.getString(idxTitle));
        question.setContent(cursor.getString(idxContent));
        question.setDescription(cursor.getString(idxDespcrition));
        question.setUserName(cursor.getString(idxUserName));
        question.setUpdateAt(cursor.getString(idxUpdateAt));

        question.setStared(cursor.getInt(idxStared) == VALUE_STARED);
        question.setUnread(cursor.getInt(idxUnread) == VALUE_UNREADED);

        return question;
    }


    private Cursor getSingleQuestionCursorByField(String field, int value) throws QuestionNotFoundException {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();

        Cursor cursor = db.query(DATABASE_QUESTIONS_TABLE_NAME, SELECT_ALL,
                field + " = " + value, null, null, null, null);
        cursor.moveToFirst();

        try {
            if (cursor.getCount() < 1) {
                throw new QuestionNotFoundException(context.getString(R.string.notfound));
            }
        } finally {
            db.close();
        }

        return cursor;
    }

    private Cursor getSingleQuestionCursorById(int id) throws QuestionNotFoundException {
        return getSingleQuestionCursorByField(COLUM_ID, id);
    }


    public Question getSingleQuestionByField(String field, int value) {
        Question question = new Question();
        Cursor cursor = getSingleQuestionCursorByField(field, value);

        try {
            getIndexFromCursor(cursor);
            question = convertCursorIntoQuestion(cursor);
        } catch (QuestionNotFoundException e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }

        return question;
    }


    /**
     * 获取单条问题
     *
     * @param id
     * @return
     * @throws QuestionNotFoundException
     */
    public Question getSingleQuestionById(int id) {
        return getSingleQuestionByField(COLUM_ID, id);
    }

    public Question getSingleQuestionByAnswerId(int id) {
        return getSingleQuestionByField(COLUM_ANSWER_ID, id);
    }

    synchronized public int markAsRead(int id) {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUM_UNREAD, VALUE_READED);

        try {
            return db.update(DATABASE_QUESTIONS_TABLE_NAME, contentValues,
                    COLUM_ID + " = ?", new String[]{String.valueOf(id)});
        } finally {
            db.close();
        }
    }


    private int getSingleIntFieldValue(int id, String field) {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getReadableDatabase();

        String sql = "SELECT " + field + " FROM " + DATABASE_QUESTIONS_TABLE_NAME +
                " WHERE " + COLUM_ID + " = " + id + " LIMIT 1";

        Cursor cursor = db.rawQuery(sql, null);
        if (cursor.getCount() != 1) {
            return -1;
        }

        try {
            cursor.moveToFirst();
            return cursor.getInt(cursor.getColumnIndex(field));
        } finally {
            cursor.close();
            db.close();
        }
    }

    public boolean isStared(int id) {
        return (getSingleIntFieldValue(id, COLUM_STARED) == VALUE_STARED) ? true : false;
    }

    synchronized public boolean isUnread(int id) {
        int value = getSingleIntFieldValue(id, COLUM_UNREAD);
        return (value == VALUE_READED) ? false : true;
    }

    synchronized public int markQuestionAsStared(int id, boolean flag) {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUM_STARED, flag ? VALUE_STARED : VALUE_UNSTARED);

        try {
            return db.update(DATABASE_QUESTIONS_TABLE_NAME, contentValues,
                    COLUM_ID + " = ?", new String[]{String.valueOf(id)});
        } finally {
            db.close();
        }
    }


    /**
     * 从 JSON 数据中直接插入到数据库
     *
     * @param question
     * @return
     * @throws JSONException
     * @throws SQLiteException
     */
    synchronized public long insertSingleQuestion(JSONObject question) throws JSONException, SQLiteException {
        SQLiteDatabase db = new DatabaseOpenHelper(context).getWritableDatabase();

        ContentValues contentValues = new ContentValues();

        contentValues.put(COLUM_ID, question.getInt(COLUM_ID));
        contentValues.put(COLUM_QUESTION_ID, question.getInt(COLUM_QUESTION_ID));
        contentValues.put(COLUM_ANSWER_ID, question.getInt(COLUM_ANSWER_ID));

        contentValues.put(COLUM_QUESTION_TITLE, question.getString(COLUM_QUESTION_TITLE));
        contentValues.put(COLUM_QUESTION_DESCRIPTION, question.getString(COLUM_QUESTION_DESCRIPTION));
        contentValues.put(COLUM_CONTENT, question.getString(COLUM_CONTENT));

        contentValues.put(COLUM_USER_NAME, question.getString(COLUM_USER_NAME));
        contentValues.put(COLUM_UPDATE_AT, question.getString(COLUM_UPDATE_AT));
        contentValues.put(COLUM_USER_AVATAR, question.getString(COLUM_USER_AVATAR));

        try {
//            ContentValues v = new ContentValues();
//            v.put(COLUM_ANSWER_ID, question.getInt(COLUM_ANSWER_ID));
//            v.put(COLUM_QUESTION_TITLE, question.getString(COLUM_QUESTION_TITLE));
//            db.insert(DATABASE_QUESTIONS_VIRTUAL_TABLE_NAME, null, v);

            return db.insert(DATABASE_QUESTIONS_TABLE_NAME, null, contentValues);
        } finally {
            db.close();
        }
    }


    /**
     * 关闭数据库
     */
    synchronized public void close() {

    }


    public class QuestionNotFoundException extends SQLiteException {
        public QuestionNotFoundException(String message) {
            super(message);
        }
    }
}
