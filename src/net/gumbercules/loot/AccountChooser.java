package net.gumbercules.loot;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class AccountChooser extends ListActivity
{
	public static final int ACTIVITY_CREATE	= 0;
	public static final int ACTIVITY_EDIT	= 1;
	
	public static final int NEW_ACCT_ID		= Menu.FIRST;
	public static final int RESTORE_ID		= Menu.FIRST + 1;
	public static final int CLEAR_ID		= Menu.FIRST + 2;
	public static final int SETTINGS_ID		= Menu.FIRST + 3;
	public static final int BACKUP_ID		= Menu.FIRST + 4;
	public static final int BU_RESTORE_ID	= Menu.FIRST + 5;
	
	public static final int CONTEXT_EDIT	= Menu.FIRST + 6;
	public static final int CONTEXT_DEL		= Menu.FIRST + 7;
	
	private static boolean copyInProgress = false;

	private ArrayList<Account> accountList;
	private UpdateThread mUpdateThread;
	
	private CharSequence[] acct_names;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.accounts);
		getListView().setOnCreateContextMenuListener(this);
		
		TransactionActivity.setAccountNull();
		accountList = new ArrayList<Account>();
		AccountAdapter accounts = new AccountAdapter(this, R.layout.account_row, accountList);
		setListAdapter(accounts);
		fillList();
		
		mUpdateThread = new UpdateThread(this);
		mUpdateThread.start();
		UpdateChecker uc = new UpdateChecker();
		uc.start();

		// automatically purge transactions on load if this option is set
		int purge_days = (int)Database.getOptionInt("auto_purge_days");
		if (purge_days != -1)
		{
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DAY_OF_YEAR, -purge_days);
			Date date = cal.getTime();
			for (Account acct : accountList)
			{
				acct.purgeTransactions(date);
			}
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		Account acct = Account.getAccountById((int)id);
		if (acct == null)
			return;
		
		Intent in = new Intent(this, TransactionActivity.class);
		in.putExtra(Account.KEY_ID, acct.id());
		startActivityForResult(in, 0);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, NEW_ACCT_ID, 0, R.string.new_account)
			.setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, RESTORE_ID, 0, R.string.restore_account)
			.setIcon(android.R.drawable.ic_menu_revert);
		menu.add(0, CLEAR_ID, 0, R.string.clear_account)
			.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, SETTINGS_ID, 0, R.string.settings)
			.setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, BACKUP_ID, 0, R.string.backup)
			.setShortcut('1', 'b')
			.setVisible(false);
		menu.add(0, BU_RESTORE_ID, 0, R.string.restore_db)
			.setShortcut('2', 'r')
			.setVisible(false);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		CopyThread ct;
    	switch (item.getItemId())
    	{
    	case NEW_ACCT_ID:
    		createAccount();
    		return true;
    		
    	case RESTORE_ID:
    		restoreAccount();
    		return true;
    		
    	case CLEAR_ID:
    		clearAccount();
    		return true;
    		
    	case SETTINGS_ID:
    		showSettings();
    		return true;
    		
    	case BACKUP_ID:
    		ct = new CopyThread(CopyThread.BACKUP, this);
    		ct.start();
    		return true;
    		
    	case BU_RESTORE_ID:
    		ct = new CopyThread(CopyThread.RESTORE, this);
    		ct.start();
    		return true;
    	}
    	
		return super.onOptionsItemSelected(item);
	}
	
	private Account[] findDeletedAccounts()
	{
		int[] ids = Account.getDeletedAccountIds();
		if (ids == null)
		{
			new AlertDialog.Builder(this)
				.setMessage(R.string.no_deleted_accounts)
				.show();
			return null;
		}
		
		int len = ids.length;
		Account[] accounts = new Account[len];
		for (int i = len - 1; i >= 0; --i)
		{
			accounts[i] = new Account();
			accounts[i].loadById(ids[i], true);
		}
		
		return accounts;
	}
	
	private void restoreAccount()
	{
		final Account[] finalAccts = findDeletedAccounts();
		if (finalAccts == null)
			return;
		int len = finalAccts.length;
		acct_names = new CharSequence[len];
		String[] split;
		for (int i = 0; i < len; ++i)
		{
			split = finalAccts[i].name.split(" - Deleted ");
			if (split != null)
				acct_names[i] = split[0];
		}
		
		new AlertDialog.Builder(this)
			.setTitle(R.string.restore)
			.setItems(acct_names, new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					Account.restoreDeletedAccount(finalAccts[which].id());
					fillList();
				}
			})
			.show();
	}

	private void clearAccount()
	{
		final Account[] finalAccts = findDeletedAccounts();
		if (finalAccts == null)
			return;
		int len = finalAccts.length;
		acct_names = new CharSequence[len];
		String[] split;
		for (int i = 0; i < len; ++i)
		{
			split = finalAccts[i].name.split(" - Deleted ");
			if (split != null)
				acct_names[i] = split[0];
		}
		
		final Context con = this;
		new AlertDialog.Builder(this)
			.setTitle(R.string.clear)
			.setItems(acct_names, new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					final int pos = which;
					AlertDialog yn_dialog = new AlertDialog.Builder(con)
						.setMessage("Are you sure you wish to remove this account completely?")
						.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int which)
							{
								Account.clearDeletedAccount(finalAccts[pos].id());
							}
						})
						.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int which) { }
						})
						.create();
					yn_dialog.show();
				}
			})
			.show();
	}

	private void createAccount()
	{
		Intent i = new Intent(this, AccountEdit.class);
    	startActivityForResult(i, ACTIVITY_CREATE);
	}
	
	private void editAccount(int id)
	{
		Intent i = new Intent(this, AccountEdit.class);
		i.putExtra(Account.KEY_ID, id);
		startActivityForResult(i, ACTIVITY_EDIT);
	}
	
	private void showSettings()
	{
		Intent i = new Intent(this, SettingsActivity.class);
		startActivityForResult(i, 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		fillList();
	}
	
	@SuppressWarnings("unchecked")
	private void fillList()
	{
		int[] acctIds = Account.getAccountIds();
		accountList.clear();
		
		if (acctIds != null)
			for ( int id : acctIds )
				accountList.add(Account.getAccountById(id));
		((ArrayAdapter<Account>)getListAdapter()).notifyDataSetChanged();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterView.AdapterContextMenuInfo info;
		try
		{
			info = (AdapterContextMenuInfo)item.getMenuInfo();
		}
		catch (ClassCastException e)
		{
			Log.e(AccountChooser.class.toString(), "Bad ContextMenuInfo", e);
			return false;
		}
		
		int id = (int)getListAdapter().getItemId(info.position);
		switch (item.getItemId())
		{
		case CONTEXT_EDIT:
			editAccount(id);
			return true;
			
		case CONTEXT_DEL:
			final Account acct = Account.getAccountById(id);
			AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.account_del_box)
				.setMessage("Are you sure you wish to delete " + acct.name + "?")
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						acct.erase();
						fillList();
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which) { }
				})
				.create();
			dialog.show();
			
			return true;
		}
		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		AdapterView.AdapterContextMenuInfo info;
		try
		{
			info = (AdapterContextMenuInfo)menuInfo;
		}
		catch (ClassCastException e)
		{
			Log.e(AccountChooser.class.toString(), "Bad ContextMenuInfo", e);
			return;
		}
		
		Account acct = (Account)getListAdapter().getItem(info.position);
		if (acct == null)
			return;
		
		menu.setHeaderTitle(acct.name);
		
		menu.add(0, CONTEXT_EDIT, 0, R.string.edit);
		menu.add(0, CONTEXT_DEL, 0, R.string.del);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (!prefs.getBoolean(PinActivity.SHOW_ACCOUNTS, true))
			finish();
	}

	private class UpdateChecker extends Thread
	{
		public static final String VERSION_CODE_DENIED	= "up_denied";
		public static final String UPDATE_REMINDER		= "up_remind";

		@Override
		public void run()
		{
			try
			{
				URL url = new URL("http", "gumbercules.net", "/loot/version.html");
				URLConnection conn = url.openConnection();
				conn.connect();
				InputStream is = conn.getInputStream();
				byte[] bytes = new byte[10];
				int len = is.read(bytes);
				int current_ver = Integer.valueOf(new String(bytes, 0, len).trim());
				PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
				
				if (current_ver > pi.versionCode)
				{
					Message msg = new Message();
					msg.what = R.string.update;
					msg.arg1 = current_ver;
					mUpdateThread.mHandler.sendMessage(msg);
				}
			}
			catch (MalformedURLException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (NameNotFoundException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private class UpdateThread extends Thread
	{
		public Handler mHandler;
		private Context mContext;
		
		public UpdateThread(Context con)
		{
			mContext = con;
		}
		
		@Override
		public void run()
		{
			Looper.prepare();
		
			mHandler = new Handler()
			{
				public void handleMessage(Message msg)
				{
					final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
					final int current_version = msg.arg1;

					// return early if the user is opting not to update right now
					long now = Calendar.getInstance().getTimeInMillis();
					long nag_date = prefs.getLong(UpdateChecker.UPDATE_REMINDER, now);
					int nag_version = prefs.getInt(UpdateChecker.VERSION_CODE_DENIED, current_version);
					if ((nag_date == -1 || now < nag_date) && current_version == nag_version)
						return;
					
					final AlertDialog.Builder reminder = new AlertDialog.Builder(mContext)
						.setMessage(R.string.nag)
						.setPositiveButton(R.string.yes,
								new AlertDialog.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int which)
									{
										Calendar cal = Calendar.getInstance();
										cal.add(Calendar.WEEK_OF_YEAR, 1);
										SharedPreferences.Editor editor = prefs.edit();
										editor.putLong(UpdateChecker.UPDATE_REMINDER, cal.getTimeInMillis());
										editor.commit();
									}
								})
						.setNegativeButton(R.string.no,
								new AlertDialog.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int which)
									{
										SharedPreferences.Editor editor = prefs.edit();
										editor.putLong(UpdateChecker.UPDATE_REMINDER, -1);
										editor.commit();
									}
								});

					
					new AlertDialog.Builder(mContext)
						.setMessage(msg.what)
						.setPositiveButton(R.string.yes,
								new AlertDialog.OnClickListener()
								{
									public void onClick(DialogInterface arg0, int arg1)
									{
										// update required
										Intent intent = new Intent(Intent.ACTION_VIEW);
										intent.setData(Uri.parse("market://search?q=Loot"));
		
										try
										{
											mContext.startActivity(intent);
										}
										catch (ActivityNotFoundException e)
										{
											Toast.makeText(mContext, "Market not available",
													Toast.LENGTH_SHORT).show();
										}
									}
								})
						.setNegativeButton(R.string.no,
                            	new AlertDialog.OnClickListener()
                            	{
                                    public void onClick(DialogInterface arg0, int arg1)
                                    {
										SharedPreferences.Editor editor = prefs.edit();
                                    	editor.putInt(UpdateChecker.VERSION_CODE_DENIED, current_version);
                                    	editor.commit();
                                    	reminder.show();
                                    }
                            	})
                    	.show();
				}
			};
			
			Looper.loop();
		}
	}
	
	private class CopyThread extends Thread
	{
		public static final int BACKUP	= 0;
		public static final int RESTORE	= 1;
		
		private Context mContext;
		private int mOp;
		
		public CopyThread(int op, Context con)
		{
			mOp = op;
			mContext = con;
		}
		
		@Override
		public void run()
		{
    		if (copyInProgress)
    			return;
    		else
    			copyInProgress = true;

			Looper.prepare();
			
    		int res = 0;
    		if (mOp == BACKUP)
			{
	    		if (Database.backup(getResources().getString(R.string.backup_path)))
	    			res = R.string.backup_successful;
	    		else
	    			res = R.string.backup_failed;
			}
			else if (mOp == RESTORE)
			{
	    		if (Database.restore(getResources().getString(R.string.backup_path)))
	    		{
	    			res = R.string.restore_successful;
	    			
	    			new Thread()
	    			{
	    				public void run()
	    				{
	    					try
	    					{
								Thread.sleep(3000);
							}
	    					catch (InterruptedException e) { }
	    					System.exit(0);
	    				}
	    			}.start();
	    		}
	    		else
	    			res = R.string.restore_failed;
			}
    		copyInProgress = false;
    		
    		if (res != 0)
    			Toast.makeText(mContext, res, Toast.LENGTH_LONG).show();
    		
    		Looper.loop();
		}
	}
}
