package net.gumbercules.loot.transaction;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;

import net.gumbercules.loot.R;
import net.gumbercules.loot.account.Account;
import net.gumbercules.loot.backend.Database;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class TransactionAdapter extends ArrayAdapter<Transaction> implements Filterable
{
	private ArrayList<Transaction> mTransList;
	private ArrayList<Transaction> mOriginalList;
	private ArrayList<Double> mRunningBalances;
	private int mRowResId;
	private Context mContext;
	private LayoutInflater mInflater;
	private static CharSequence mConstraint;
	private int mAcctId;
	private DateFormat mDf;
	private static NumberFormat mNf = null;
	
	// preferences
	private boolean mShowColors;
	private boolean mColorBackgrounds;
	private boolean mColorSide;
	private boolean mShowRunningBalance;
	private boolean mTopSort;
	
	private static final int COLOR_BUDGET	= 1;
	private static final int COLOR_CHECK	= 1 << 1;
	private static final int COLOR_WITHDRAW	= 1 << 2;
	private static final int COLOR_DEPOSIT	= 1 << 3;
	
	private HashMap<Integer, Integer> mColors;
	
	public TransactionAdapter(Context con, int row, ArrayList<Transaction> tr, int acct_id)
	{
		super(con, 0);
		if (tr == null)
		{
			tr = new ArrayList<Transaction>();
		}
		this.mTransList = tr;
		this.mOriginalList = tr;
		this.mRunningBalances = new ArrayList<Double>();
		this.mRowResId = row;
		this.mContext = con;
		this.mAcctId = acct_id;
		this.mDf = DateFormat.getDateInstance();
		mNf = NumberFormat.getCurrencyInstance();
		String new_currency = Database.getOptionString("override_locale");
		if (new_currency != null && !new_currency.equals("") &&
				!new_currency.equals(mNf.getCurrency().getCurrencyCode()))
		{
			mNf.setCurrency(Currency.getInstance(new_currency));
		}

		mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public void updatePreferenceValues()
	{
		final int Color_LTGREEN = Color.rgb(185, 255, 185);
		final int Color_LTYELLOW = Color.rgb(255, 255, 185);
		final int Color_LTCYAN = Color.rgb(185, 255, 255);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		mShowColors = prefs.getBoolean("color", true);
		mColorBackgrounds = prefs.getBoolean("color_background", true);
		mColorSide = prefs.getBoolean("color_bg_side", false);
		mShowRunningBalance = prefs.getBoolean("running_balance", false);
		
		mColors = new HashMap<Integer, Integer>();
		
		int[] colors = new int[6];
		colors[0] = prefs.getInt("color_check", prefs.getInt("ac_color", Color.CYAN)); 
		colors[1] = prefs.getInt("color_budget_check", prefs.getInt("bc_color", Color_LTCYAN));
		colors[2] = prefs.getInt("color_withdraw", prefs.getInt("aw_color", Color.YELLOW));
		colors[3] = prefs.getInt("color_budget_withdraw", prefs.getInt("bw_color", Color_LTYELLOW));
		colors[4] = prefs.getInt("color_deposit", prefs.getInt("ad_color", Color.GREEN));
		colors[5] = prefs.getInt("color_budget_deposit", prefs.getInt("bd_color", Color_LTGREEN));
		
		int[] color_spec = new int[6];
		color_spec[0] = COLOR_CHECK;
		color_spec[1] = COLOR_CHECK | COLOR_BUDGET;
		color_spec[2] = COLOR_WITHDRAW;
		color_spec[3] = COLOR_WITHDRAW | COLOR_BUDGET;
		color_spec[4] = COLOR_DEPOSIT;
		color_spec[5] = COLOR_DEPOSIT | COLOR_BUDGET;
		
		for (int i = 0; i < colors.length; ++i)
		{
			mColors.put(color_spec[i], colors[i]);
		}
		
		mTopSort = prefs.getBoolean("top_sort", false);
	}
	
	public void calculateRunningBalances()
	{
		if (mTopSort)
		{
			calculateRunningBalancesTopSort();
		}
		else
		{
			calculateRunningBalancesBottomSort();
		}
	}

	public void calculateRunningBalances(int pos)
	{
		if (mTopSort)
		{
			calculateRunningBalancesTopSort(pos);
		}
		else
		{
			calculateRunningBalancesBottomSort(pos);
		}
	}
	
	private void calculateRunningBalancesTopSort()
	{
		calculateRunningBalancesTopSort(-1);
	}
	
	private void calculateRunningBalancesTopSort(int pos)
	{
		if (!mShowRunningBalance)
		{
			return;
		}

		int len = mOriginalList.size();
		int len_diff = mRunningBalances.size() - len;

		for (int i = 0; i < len_diff; ++i)
		{
			mRunningBalances.remove(0);
		}

		if (!mRunningBalances.isEmpty())
		{
			mRunningBalances.subList(0, pos + 1).clear();
		}
		
		len_diff = (len_diff > 0 ? 0 : len_diff);
		
		double cur_balance, prev_balance, amount;
		Transaction trans;
		
		if (pos == (len - 1) || mRunningBalances.isEmpty())
		{
			Account acct = Account.getAccountById(mAcctId);
			prev_balance = acct.initialBalance;
		}
		else
		{
			prev_balance = mRunningBalances.get(0);
		}
		
		int start = (pos == -1 ? len - 1 : pos - len_diff);
		
		for (int i = start; i >= 0; --i)
		{
			if (i > mOriginalList.size())
			{
				continue;
			}
			
			trans = mOriginalList.get(i);
			if (trans.type == Transaction.DEPOSIT)
			{
				amount = trans.amount;
			}
			else
			{
				amount = -trans.amount;
			}
			
			cur_balance = prev_balance + amount;
			mRunningBalances.add(0, cur_balance);
			
			prev_balance = cur_balance;
		}
		
		notifyDataSetChanged();
	}
	
	private void calculateRunningBalancesBottomSort()
	{
		calculateRunningBalancesBottomSort(0);
	}
	
	private void calculateRunningBalancesBottomSort(int pos)
	{
		// don't waste time with the calculations if it's not being shown
		if (!mShowRunningBalance)
		{
			return;
		}
		
		if (!mRunningBalances.isEmpty())
		{
			int sz = mRunningBalances.size();
			mRunningBalances.subList(pos, sz).clear(); 
		}
		
		int len = mOriginalList.size();
		double cur_balance, prev_balance, amount;
		Transaction trans;
		
		if (pos == 0 || mRunningBalances.isEmpty())
		{
			Account acct = Account.getAccountById(mAcctId);
			prev_balance = acct.initialBalance;
		}
		else
		{
			prev_balance = mRunningBalances.get(pos - 1);
		}
		
		for (int i = pos; i < len; ++i)
		{
			trans = mOriginalList.get(i);
			if (trans.type == Transaction.DEPOSIT)
			{
				amount = trans.amount;
			}
			else
			{
				amount = -trans.amount;
			}
			
			cur_balance = prev_balance + amount;
			mRunningBalances.add(cur_balance);
			
			prev_balance = cur_balance;
		}
		
		notifyDataSetChanged();
	}

	public void setContext(Context con)
	{
		this.mContext = con;
	}
	
	@Override
	public int getCount()
	{
		if (mTransList == null)
		{
			return 0;
		}
		
		return mTransList.size();
	}

	@Override
	public Transaction getItem(int position)
	{
		if (position == -1)
			return null;
		return mTransList.get(position);
	}

	@Override
	public long getItemId(int position)
	{
		return mTransList.get(position).id();
	}
	
	public int findItemNoFilter(int id)
	{
		for (int i = 0; i < mOriginalList.size(); ++i)
		{
			if (mOriginalList.get(i).id() == id)
				return i;
		}
		
		return -1;
	}
	
	public int findItemByDate(Date d)
	{
		for (int i = 0; i < mTransList.size(); ++i)
		{
			if (mTransList.get(i).date.after(d))
			{
				return i - 1;
			}
		}
		
		return -1;
	}
	
	public int findItemById(int id)
	{
		for (int i = 0; i < mTransList.size(); ++i)
		{
			if (mTransList.get(i).id() == id)
				return i;
		}
		
		return -1;
	}

	public void setResource(int row)
	{
		this.mRowResId = row;
	}
	
	public void setList(ArrayList<Transaction> trans)
	{
		mOriginalList = trans;
		new TransactionFilter()._filter(mConstraint);
		notifyDataSetChanged();
	}
	
	public ArrayList<Transaction> getList()
	{
		return mTransList;
	}
	
	public void sort()
	{
		Collections.sort(mOriginalList);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		if (prefs.getBoolean("top_sort", false))
		{
			Collections.reverse(mOriginalList);
		}
		new TransactionFilter()._filter(mConstraint);
		notifyDataSetChanged();
	}

	@Override
	public void add(Transaction object)
	{
		if (object.account == mAcctId)
		{
			mOriginalList.add(object);
			notifyDataSetChanged();
		}
	}
	
	public void add(int[] ids)
	{
		if (ids == null)
		{
			return;
		}
		
		int last = -1;
		Transaction trans;
		for (int id : ids)
		{
			if (id == -1 || id == last)
				continue;
			
			trans = Transaction.getTransactionById(id);
			if (trans != null && trans.account == mAcctId)
			{
				mOriginalList.add(trans);
			}
			
			last = id;
		}
		notifyDataSetChanged();
	}

	public void add(Transaction[] trans_list)
	{
		if (trans_list == null)
		{
			return;
		}
		
		Transaction trans;
		for (int i = trans_list.length - 1; i >= 0; --i)
		{
			trans = trans_list[i];
			if (trans != null && trans.account == mAcctId)
			{
				mOriginalList.add(trans);
			}
		}
		notifyDataSetChanged();
	}

	@Override
	public void insert(Transaction object, int index)
	{
		if (object.account == mAcctId)
		{
			mOriginalList.add(index, object);
			notifyDataSetChanged();
		}
	}

	@Override
	public void remove(Transaction object)
	{
		mOriginalList.remove(object);
		new TransactionFilter()._filter(mConstraint);
		notifyDataSetChanged();
	}
	
	public void remove(int[] ids)
	{
		for (int id : ids)
		{
			mOriginalList.remove(getItem(findItemById(id)));
		}
		new TransactionFilter()._filter(mConstraint);
		notifyDataSetChanged();
	}
	
	@Override
	public void clear()
	{
		mOriginalList.clear();
		new TransactionFilter()._filter(mConstraint);
		notifyDataSetChanged();
	}
	
	@Override
	public Filter getFilter()
	{
		TransactionFilter filter = new TransactionFilter();
		return (Filter)filter;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ViewHolder holder;
		
		if (convertView == null)
		{
			convertView = mInflater.inflate(mRowResId, parent, false);
			
			holder = new ViewHolder();
			holder.check = (CheckBox)convertView.findViewById(R.id.PostedCheckBox);
			holder.date = (TextView)convertView.findViewById(R.id.DateText);
			holder.party = (TextView)convertView.findViewById(R.id.PartyText);
			holder.amount = (TextView)convertView.findViewById(R.id.AmountText);
			holder.running_balance = (TextView)convertView.findViewById(R.id.RunningBalanceText);
			holder.image = (ImageView)convertView.findViewById(R.id.image_view);
			holder.top = (LinearLayout)convertView.findViewById(R.id.LinearLayout01);
			holder.sidebar = (LinearLayout)convertView.findViewById(R.id.SideBar);
			
			convertView.setTag(holder);
		}
		else
		{
			holder = (ViewHolder)convertView.getTag();
		}

		// bail early if the transaction doesn't exist, isn't for this account, or is not currently visible
		final Transaction trans = mTransList.get(position);
		if (trans == null || trans.account == 0)
		{
			return convertView;
		}
		
		final int pos = position;

		// find and retrieve the widgets
		CheckBox postedCheck = holder.check;
		
		if (postedCheck == null)
		{
			return convertView;
		}
		
		postedCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (trans.isPosted() != isChecked)
				{
					boolean budget = trans.budget;
					trans.post(isChecked);
					TransactionActivity ta = (TransactionActivity) mContext;
					ta.setBalances();

					Intent broadcast = new Intent("net.gumbercules.loot.intent.ACCOUNT_UPDATED", null);
					broadcast.putExtra("account_id", trans.account);
					TransactionAdapter.this.getContext().sendBroadcast(broadcast);

					// only need to update the view if it changed from budget to posted
					if (budget)
					{
						ListView lv = ta.getListView();
						View v = lv.getChildAt(pos - lv.getFirstVisiblePosition());
						if (v != null)
						{
							ViewHolder holder = (ViewHolder)v.getTag();
							setViewData(trans, holder, null, null, null);
						}
					}
				}
			}
		});
		
		// change the date to the locale date format
		DateFormat df = mDf;
		String dateStr = df.format(trans.date);
		
		// change the numbers to the locale currency format
		NumberFormat nf = mNf;

		// if not showing the prefix, make sure to specify difference between positive/negative values
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		double amount = (trans.type == Transaction.DEPOSIT ? trans.amount : -trans.amount);
		if (prefs.getBoolean("prefix_party", false))
		{
			amount = Math.abs(trans.amount);
		}
		
		String amountStr = nf.format(amount);
		String balStr = null;
		if (mShowRunningBalance)
		{
			try
			{
				balStr = nf.format(mRunningBalances.get(pos));
			}
			catch (IndexOutOfBoundsException e)
			{
				calculateRunningBalances();
				balStr = nf.format(mRunningBalances.get(pos));
			}
		}
		
		if (postedCheck != null)
		{
			postedCheck.setChecked(trans.isPosted());
		}

		// populate the widgets with data
		setViewData(trans, holder, amountStr, dateStr, balStr);
		
		return convertView;
	}
	
	private void setViewData(Transaction trans, ViewHolder v, String amountStr, String dateStr, String balStr)
	{
		String partyStr = "";
		int color = Color.LTGRAY;
		
		if (trans.budget)
		{
			if (trans.type == Transaction.DEPOSIT)
			{
				partyStr += "+";
			}
			else
			{
				partyStr += "-";
			}
		}
		
		Account acct = Account.getAccountById(trans.account);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean prefix = prefs.getBoolean("prefix_party", false);
		
		if (trans.type == Transaction.CHECK)
		{
			partyStr += trans.check_num + ":";
			color = COLOR_CHECK;
		}
		else if (trans.type == Transaction.WITHDRAW)
		{
			if (prefix)
			{
				if (acct.credit)
				{
					partyStr += "C:";
				}
				else
				{
					partyStr += "W:";
				}
			}
			color = (acct.credit ? COLOR_DEPOSIT : COLOR_WITHDRAW);
		}
		else
		{
			if (prefix)
			{
				partyStr += "D:";
			}
			color = (acct.credit ? COLOR_WITHDRAW : COLOR_DEPOSIT);
		}
		if (trans.budget)
		{
			color |= COLOR_BUDGET;
		}
		
		partyStr += trans.party;
		
		TextView dateText = v.date;
		TextView partyText = v.party;
		TextView amountText = v.amount;
		TextView runningBalanceText = v.running_balance;
		
		if (mShowColors && mColorBackgrounds)
		{
			if (mColorSide)
			{
				v.sidebar.setBackgroundColor(mColors.get(color));
				v.sidebar.setVisibility(View.VISIBLE);
				v.top.setBackgroundDrawable(null);
			}
			else
			{
				v.sidebar.setVisibility(View.GONE);
				v.top.setBackgroundColor(mColors.get(color));
			}
		}
		else
		{
			v.top.setBackgroundDrawable(null);
		}
		
		if (dateStr == null)
		{
			dateStr = v.date.getText().toString();
		}
		if (amountStr == null)
		{
			amountStr = v.amount.getText().toString();
		}
		
		setText(dateText, dateStr, color, mShowColors, mColorBackgrounds);
		setText(partyText, partyStr, color, mShowColors, mColorBackgrounds);
		setText(amountText, amountStr, color, mShowColors, mColorBackgrounds);
		
		if (mShowRunningBalance)
		{
			if (balStr == null)
			{
				balStr = v.running_balance.getText().toString();
			}
			setText(runningBalanceText, balStr, color, mShowColors, mColorBackgrounds);
			runningBalanceText.setVisibility(View.VISIBLE);
		}
		else
		{
			runningBalanceText.setVisibility(View.GONE);
		}
		
		if (trans.images != null && trans.images.size() > 0)
		{
			v.image.setVisibility(View.VISIBLE);
		}
		else
		{
			v.image.setVisibility(View.GONE);
		}
	}

	private void setText(TextView text, String str, int color_key, boolean colors, boolean bg)
	{
		if (text != null)
		{
			text.setText(str);
		}

		if (colors)
		{
			int color = mColors.get(color_key);
			if (bg && !mColorSide)
			{
				int red = Color.red(color);
				int green = Color.green(color);
				int blue = Color.blue(color);
				
				int max = Math.max(red, Math.max(green, blue));
				int min = Math.min(red, Math.min(green, blue));
				
				float lightness = 0.5f * (float)(max + min);
				
				if (lightness < 100.0f)
					color = Color.LTGRAY;
				else
					color = Color.DKGRAY;
			}
			text.setTextColor(color);
		}
	}
	
	static class ViewHolder
	{
		CheckBox check;
		TextView date;
		TextView party;
		TextView amount;
		TextView running_balance;
		ImageView image;
		LinearLayout top;
		LinearLayout sidebar;
	}

	public class TransactionFilter extends Filter
	{
		private boolean mShowPosted;
		private boolean mShowNonPosted;
		
		public TransactionFilter()
		{
			super();
			mShowPosted = true;
			mShowNonPosted = true;
		}
		
		public FilterResults filtering(CharSequence constraint)
		{
			return performFiltering(constraint);
		}
		
		public void setShowPosted(boolean b)
		{
			mShowPosted = b;
		}
		
		public void setShowNonPosted(boolean b)
		{
			mShowNonPosted = b;
		}
		
		@Override
		protected FilterResults performFiltering(CharSequence constraint)
		{
			mConstraint = constraint;
			
			// do the filtering to decide which items we show
			if (constraint == null || constraint.length() == 0)
			{
				return filterEmptyString();
			}
			else
			{
				return filterString(constraint);
			}
		}
		
		private FilterResults filterEmptyString()
		{
			FilterResults results = new FilterResults();
			ArrayList<Transaction> tList = new ArrayList<Transaction>(mOriginalList);
			ArrayList<Transaction> values = new ArrayList<Transaction>();
			results.count = 0;
			results.values = values;
			
			for (Transaction trans : tList)
			{
				if ((!mShowPosted && trans.isPosted()) || (!mShowNonPosted && !trans.isPosted()))
				{
					continue;
				}
				
				values.add(trans);
			}

			results.values = (Object)values;
			results.count = values.size();
			
			return results;
		}
		
		private FilterResults filterString(CharSequence constraint)
		{
			FilterResults results = new FilterResults();

			boolean matches = false;
			ArrayList<Transaction> tList = new ArrayList<Transaction>(mOriginalList);
			ArrayList<Transaction> values = new ArrayList<Transaction>();
			results.count = 0;
			results.values = values;
			String[] filters = constraint.toString().split(" ");
			
			for (Transaction trans : tList)
			{
				// if it doesn't match the posted/non-posted options, exit
				if ((!mShowPosted && trans.isPosted()) || (!mShowNonPosted && !trans.isPosted()))
				{
					continue;
				}
				
				// check for at least one match
				for (String filter : filters)
				{
					filter = "(?i).*" + filter + ".*";
					if (trans.party.matches(filter))
					{
						matches = true;
						break;
					}
					for (String tag : trans.tags)
					{
						if (tag.matches(filter))
						{
							matches = true;
							break;
						}
					}
					if (matches)
						break;
				}
				
				if (matches)
				{
					values.add(trans);
				}
				
				matches = false;
			}
			
			results.values = (Object)values;
			results.count = values.size();

			return results;
		}

		public void publish(CharSequence constraint, FilterResults results)
		{
			publishResults(constraint, results);
		}
		
		@SuppressWarnings("unchecked")
		public void _filter(CharSequence cs)
		{
			filter(cs);
			
			if (cs == null || cs.equals(""))
			{
				mTransList = (ArrayList<Transaction>) mOriginalList.clone();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results)
		{
			mTransList = (ArrayList<Transaction>)results.values;
			notifyDataSetChanged();
		}
	}
}
