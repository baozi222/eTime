package lightsns.db;

import lightsns.model.UserInfo;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SqliteHelper extends SQLiteOpenHelper {
	// ��������userid,accessToken access Secret �ı���

	public static final String TB_Name = "users";

	public SqliteHelper(Context context, String name, CursorFactory factory,
			int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// db.execSQL("Create Table IF Not Exists " + this.TB_Name + " ("
		// + UserInfo.ID + "  INTEGER PRIMARY KEY AUTOINCREMENT," +
		// UserInfo.USERID
		// + "  varchar," + UserInfo.USERNAME + "  varchar,"
		// + UserInfo.TOKEN + "  varchar," + UserInfo.TOKENSECRET
		// + " varchar,  " + UserInfo.USERICON + "  blob )");

		db.execSQL("CREATE TABLE IF NOT EXISTS " + TB_Name + "(" + UserInfo.ID
				+ " integer primary key," + UserInfo.USERID
				+ " varchar," + UserInfo.TOKEN + " varchar,"
				+ UserInfo.TOKENSECRET + " varchar," + UserInfo.USERNAME
				+ " varchar," + UserInfo.USERICON + " blob" + ")");

		Log.e("Database", "onCreate");
	}

	// ���±�
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TB_Name);
		onCreate(db);
		Log.e("Database", "onUpgrade");
	}

	// ������
	public void updateColumn(SQLiteDatabase db, String oldColumn,
			String newColumn, String typeColumn) {
		try {
			db.execSQL("ALTER TABLE " + TB_Name + " CHANGE " + oldColumn + " "
					+ newColumn + " " + typeColumn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}