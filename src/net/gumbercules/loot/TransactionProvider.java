package net.gumbercules.loot;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

public class TransactionProvider extends ContentProvider
{
	public static final Uri CONTENT_URI = Uri.parse("content://net.gumbercules.loot.transactionprovider");
	public static final String TRANSACTION_ITEM_MIME = "vnd.android.cursor.item/vnd.gumbercules.transaction";
	public static final String TRANSACTION_DIR_MIME = "vnd.android.cursor.dir/vnd.gumbercules.transaction";
	public static final String INTEGER_ITEM_MIME = "vnd.android.cursor.item/vnd.gumbercules.integer";
	public static final String FLOAT_ITEM_MIME = "vnd.android.cursor.item/vnd.gumbercules.float";
	public static final String TAG_DIR_MIME = "vnd.android.cursor.dir/vnd.gumbercules.tag";

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		return 0;
	}

	@Override
	public String getType(Uri uri)
	{
		if (!uri.getScheme().equals("content"))
			return null;
		
		List<String> path = uri.getPathSegments();
		int size = path.size();
		
		String object = null;
		if (size > 0)
			object = path.get(0);
		else
			return null;
		
		boolean tag = false;
		if (object.equals("transaction"))
			tag = false;
		else if (object.equals("tag"))
			tag = true;
		else
			return null;
		
		if (size == 2)
		{
			object = path.get(1);
			if (Integer.getInteger(object) != null)
			{
				if (tag)
					return null;
				else
					return TRANSACTION_ITEM_MIME;
			}
			
			if (object.equals("count"))
				return INTEGER_ITEM_MIME;
			else if (object.equals("sum"))
				return FLOAT_ITEM_MIME;
			else
				return null;
		}
		
		return (tag ? TAG_DIR_MIME : TRANSACTION_DIR_MIME);
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		if (getType(uri) != TRANSACTION_DIR_MIME)
			return null;
		
		Transaction trans = new Transaction();
		trans.account = values.getAsInteger("account");
		
		if (values.containsKey("check_num"))
		{
			trans.check_num = values.getAsInteger("check_num");
		}
		else
		{
			trans.check_num = -1;
		}
		
		trans.amount = values.getAsDouble("amount");
		if (trans.amount < 0.0)
		{
			trans.amount = -trans.amount;
			if (trans.check_num > 0)
			{
				trans.type = Transaction.CHECK;
			}
			else
			{
				trans.type = Transaction.WITHDRAW;
			}
		}
		else
		{
			trans.type = Transaction.DEPOSIT;
		}
				
		trans.party = values.getAsString("party");
		trans.date = new Date(values.getAsLong("date"));
		
		if (values.containsKey("tags"))
		{
			trans.addTags(values.getAsString("tags"));
		}
		
		int id = trans.write(trans.account);
		
		return uri.buildUpon().appendPath(new Integer(id).toString()).build();
	}

	@Override
	public boolean onCreate()
	{
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder)
	{
		SQLiteDatabase lootDb = Database.getDatabase();
		String type = getType(uri);
		
		if (type == null)
		{
			Log.i("net.gumbercules.loot.TransactionProvider.query", "getType() returned invalid type");
			return null;
		}
		
		if (projection == null)
			projection = new String[] {"*"};
			
		if (selection == null)
			selection = "1=1";
		
		if (sortOrder == null)
			sortOrder = "id asc";
			
		List<String> path = uri.getPathSegments();
		String query = "select " + Arrays.toString(projection).replaceAll("[\\[\\]]", "") + " from ";
		if (type.equals(TRANSACTION_ITEM_MIME))
		{
			query += "transactions left outer join tags on trans_id = id " +
					"where id = " + path.get(2) + " and " + selection + " order by " + sortOrder;
		}
		else if (type.equals(TRANSACTION_DIR_MIME))
		{
			query += "transactions left outer join tags on trans_id = id " +
					"where " + selection + " order by " + sortOrder;
		}
		else if (type.equals(TAG_DIR_MIME))
		{
			if (sortOrder.equals("id asc"))
				sortOrder = "trans_id asc";
			
			query += "tags where " + selection + " order by " + sortOrder; 
		}
		else if (type.equals(INTEGER_ITEM_MIME))
		{
			String table = path.get(1) + "s";
			
			query = "select count(*) from " + table + " where " + selection;
		}
		else if (type.equals(FLOAT_ITEM_MIME))
		{
			String table = "transactions";
			if (path.get(1).equals("tag"))
			{
				table += ", tags";
				selection = "trans_id = id and " + selection;
			}
			
			query = "select sum(*) from " + table + " where " + selection;
		}
		
		Log.i("net.gumbercules.loot.TransactionProvider.query", query);
		
		return lootDb.rawQuery(query, selectionArgs);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs)
	{
		return 0;
	}

}
