package net.gumbercules.loot;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
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
	public static final int EXPORT_ID		= Menu.FIRST + 6;
	public static final int CHART_ID		= Menu.FIRST + 7;
	
	public static final int CONTEXT_EDIT	= Menu.FIRST + 8;
	public static final int CONTEXT_DEL		= Menu.FIRST + 9;
	public static final int CONTEXT_EXPORT	= Menu.FIRST + 10;
	public static final int CONTEXT_CHART	= Menu.FIRST + 11;
	public static final int CONTEXT_IMPORT	= Menu.FIRST + 12;
	
	private static final String TAG			= "net.gumbercules.loot.AccountChooser"; 
	private static boolean copyInProgress	= false;

	private ArrayList<Account> accountList;
	private TextView mTotalBalance;
	private LinearLayout mTbLayout;
	
	private CharSequence[] acct_names;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		// required to prevent last-used from jumping back to this spot
		@SuppressWarnings("unused")
		Bundle bun = getIntent().getExtras();
		
		getListView().setOnCreateContextMenuListener(this);
		
		TransactionActivity.setAccountNull();
		accountList = new ArrayList<Account>();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		ContentResolver cr = getContentResolver();

		// remove preferences if the premium package has been removed
		if (cr.getType(Uri.parse("content://net.gumbercules.loot.premium.settingsprovider/settings")) == null)
		{
			SharedPreferences.Editor editor = prefs.edit();
			String[] pref_keys = {"color_withdraw", "color_budget_withdraw", "color_deposit",
					"color_budget_deposit", "color_check", "color_budget_check", 
					"cal_enabled", "calendar_tag"};
			for (String key : pref_keys)
				editor.remove(key);
			editor.commit();
		}
		
		if (Database.getOptionInt("nag_donate") < 1)
		{
			new AlertDialog.Builder(this)
				.setMessage(R.string.new_version)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						donate();
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						donateReminder();
					}
				})
				.setCancelable(false)
				.show();
			Database.setOption("nag_donate", 1);
		}
		
		// if we're not overriding locale, check to see if the detected one is valid
		String locale = Database.getOptionString("override_locale");
		if (locale == null || locale.equals(""))
			checkLocale();

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
	
	private void checkLocale()
	{
		Currency cur = NumberFormat.getInstance().getCurrency();

		if (cur.getCurrencyCode().equalsIgnoreCase("xxx"))
		{
			new AlertDialog.Builder(this)
				.setMessage(R.string.no_locale)
				.show();
		}
	}

	private void donate()
	{
		startActivity(new Intent(this, DonateActivity.class));
	}
	
	private void donateReminder()
	{
		new AlertDialog.Builder(this)
			.setMessage(R.string.donate_location)
			.show();
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
		menu.add(0, NEW_ACCT_ID, 0, R.string.new_account)
			.setShortcut('1', 'n')
			.setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, RESTORE_ID, 0, R.string.restore_account)
			.setShortcut('2', 'h')
			.setIcon(android.R.drawable.ic_menu_revert);
		menu.add(0, CLEAR_ID, 0, R.string.clear_account)
			.setShortcut('3', 'c')
			.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, BACKUP_ID, 0, R.string.backup)
			.setShortcut('4', 'b')
			.setIcon(android.R.drawable.ic_menu_save);
		menu.add(0, BU_RESTORE_ID, 0, R.string.restore_db)
			.setShortcut('5', 'r')
			.setIcon(android.R.drawable.ic_menu_set_as);
		menu.add(0, EXPORT_ID, 0, R.string.export)
			.setShortcut('6', 'x')
			.setIcon(android.R.drawable.ic_menu_upload);
		menu.add(0, CHART_ID, 0, R.string.chart)
			.setShortcut('7', 'g')
			.setIcon(android.R.drawable.ic_menu_report_image);
		menu.add(0, SETTINGS_ID, 0, R.string.settings)
			.setShortcut('8', 's')
			.setIcon(android.R.drawable.ic_menu_preferences);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		CopyThread ct;
		ProgressDialog pd = new ProgressDialog(this);
		pd.setCancelable(true);
		pd.setMax(100);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		
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
    		ct = new CopyThread(CopyThread.BACKUP, pd, this);
    		pd.setMessage(getResources().getText(R.string.backing_up));
    		pd.show();
    		ct.start();
    		return true;
    		
    	case BU_RESTORE_ID:
    		ct = new CopyThread(CopyThread.RESTORE, pd, this);
    		pd.setMessage(getResources().getText(R.string.restoring));
    		pd.show();
    		ct.start();
    		return true;
    		
    	case EXPORT_ID:
    		PremiumCaller export = new PremiumCaller(this);
    		export.showActivity(PremiumCaller.EXPORT);
    		return true;
    		
    	case CHART_ID:
    		PremiumCaller graph = new PremiumCaller(this);
    		graph.showActivity(PremiumCaller.CHART);
    		return true;
    	}
    	
		return false;
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
	
	private void fillList()
	{
		int[] acctIds = Account.getAccountIds();
		accountList.clear();
		
		if (acctIds != null)
		{
			for ( int id : acctIds )
			{
				accountList.add(Account.getAccountById(id));
			}
		}
		AccountAdapter aa = (AccountAdapter)getListAdapter();
		if (aa == null)
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			
			int row_res = R.layout.account_row;
			if (prefs.getBoolean("large_fonts", false))
			{
				row_res = R.layout.account_row_large;
			}
			aa = new AccountAdapter(this, row_res, accountList);
			setListAdapter(aa);
			
			Log.i(TAG + ".fillList()", "i used to crash here, but now i don't");
			// TODO: remove before packaging
			Toast.makeText(this, "I USED TO CRASH HERE, BUT NOW I DON'T. YAAAAY ME!", Toast.LENGTH_LONG).show();
		}
		aa.notifyDataSetChanged();
		setTotalBalance();
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
			
		case CONTEXT_IMPORT:
			PremiumCaller imp = new PremiumCaller(this);
			imp.showActivity(PremiumCaller.IMPORT, id);
			return true;
			
		case CONTEXT_EXPORT:
			PremiumCaller export = new PremiumCaller(this);
			export.showActivity(PremiumCaller.EXPORT, id);
			return true;
			
		case CONTEXT_CHART:
			PremiumCaller graph = new PremiumCaller(this);
			graph.showActivity(PremiumCaller.CHART, id);
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
		menu.add(0, CONTEXT_IMPORT, 0, R.string.import_);
		menu.add(0, CONTEXT_EXPORT, 0, R.string.export);
		menu.add(0, CONTEXT_CHART, 0, R.string.chart);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (!prefs.getBoolean(PinActivity.SHOW_ACCOUNTS, true))
			finish();
		
		int row_res = R.layout.account_row;
		if (prefs.getBoolean("large_fonts", false))
		{
			row_res = R.layout.account_row_large;
		}

		setContent();
		
		registerForContextMenu(getListView());
		AccountAdapter accounts = new AccountAdapter(this, row_res, accountList);
		setListAdapter(accounts);
		fillList();
	}
	
	private void setContent()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int content_res = R.layout.accounts;
		if (prefs.getBoolean("large_fonts", false))
		{
			content_res = R.layout.accounts_large;
		}
		
		setContentView(content_res);
		mTbLayout = (LinearLayout)findViewById(R.id.total_balance_layout);
		mTotalBalance = (TextView)findViewById(R.id.total_balance);
	}
	
	private void setTotalBalance()
	{	
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("total_balance", true))
		{
			NumberFormat nf = NumberFormat.getCurrencyInstance();
			String new_currency = Database.getOptionString("override_locale");
			if (new_currency != null && !new_currency.equals(""))
				nf.setCurrency(Currency.getInstance(new_currency));
			Double bal = Account.getTotalBalance();
			String text;
			if (bal != null)
			{
				text = nf.format(bal);
			}
			else
			{
				text = "Error Calculating Balance";
			}
			mTotalBalance.setText(text);
			
			if (bal < 0.0)
			{
				if (prefs.getBoolean("color_balance", false))
					mTotalBalance.setTextColor(Color.rgb(255, 50, 50));
			}
			else
			{
				mTotalBalance.setTextColor(Color.LTGRAY);
			}

			mTbLayout.setVisibility(LinearLayout.VISIBLE);
		}
		else
		{
			mTbLayout.setVisibility(LinearLayout.GONE);
		}
	}

	private class CopyThread extends Thread
	{
		public static final int BACKUP	= 0;
		public static final int RESTORE	= 1;
		
		private Context mContext;
		private int mOp;
		private ProgressDialog mPd;
		
		public CopyThread(int op, ProgressDialog pd, Context con)
		{
			mOp = op;
			mContext = con;
			mPd = pd;
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
    			String backup_path = getResources().getString(R.string.backup_path);
    			FileWatcherThread fwt = new FileWatcherThread(Database.getDbPath(), backup_path, mPd);
    			fwt.start();
    			
	    		if (Database.backup(backup_path))
	    		{
	    			res = R.string.backup_successful;
	    			mPd.setProgress(100);
	    		}
	    		else
	    		{
	    			res = R.string.backup_failed;
	    		}
			}
			else if (mOp == RESTORE)
			{
    			String backup_path = getResources().getString(R.string.backup_path);
    			FileWatcherThread fwt = new FileWatcherThread(backup_path, Database.getDbPath(), mPd);
    			fwt.start();

    			if (Database.restore(backup_path))
	    		{
	    			res = R.string.restore_successful;
	    			mPd.setProgress(100);
	    			
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
	    		{
	    			res = R.string.restore_failed;
	    		}
			}
    		mPd.dismiss();
    		copyInProgress = false;
    		
    		if (res != 0)
    		{
    			Toast.makeText(mContext, res, Toast.LENGTH_LONG).show();
    		}
    		
    		Looper.loop();
		}
	}
	
	private class FileWatcherThread extends Thread
	{
		private ProgressDialog mPd;
		private String mFilename;
		private long mTarget;
		
		public FileWatcherThread(String from_fn, String fn, ProgressDialog pd)
		{
			File fromFile = new File(from_fn);
			mTarget = fromFile.length();
			
			mFilename = fn;
			mPd = pd;
		}
		
		@Override
		public void run()
		{
			File mFile;
			
			try
			{
				int progress = 0;
				while (progress < 100)
				{
					mPd.setProgress(progress);
					Thread.sleep(100);
					mFile = new File(mFilename);
					progress = (int)(((float)mFile.length() / mTarget) * 100);
				}
			}
			catch (InterruptedException e) { }
		}
	}
}
