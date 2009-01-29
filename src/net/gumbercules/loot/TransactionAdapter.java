package net.gumbercules.loot;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
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
import android.widget.ListView;
import android.widget.TextView;

public class TransactionAdapter extends ArrayAdapter<Transaction> implements Filterable
{
	private ArrayList<Transaction> transList;
	private ArrayList<Transaction> mOriginalList;
	private int rowResId;
	private Context context;
	private LayoutInflater mInflater;
	private static CharSequence mConstraint;
	private int mAcctId;
	private DateFormat mDf;
	private NumberFormat mNf;
	
	public TransactionAdapter(Context con, int row, ArrayList<Transaction> tr, int acct_id)
	{
		super(con, 0);
		this.transList = tr;
		this.mOriginalList = tr;
		this.rowResId = row;
		this.context = con;
		this.mAcctId = acct_id;
		this.mDf = DateFormat.getDateInstance();
		this.mNf = NumberFormat.getCurrencyInstance();
		
		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public int getCount()
	{
		return transList.size();
	}

	@Override
	public Transaction getItem(int position)
	{
		return transList.get(position);
	}

	@Override
	public long getItemId(int position)
	{
		return transList.get(position).id();
	}
	
	public int findItemById(int id)
	{
		for (int i = 0; i < transList.size(); ++i)
		{
			if (transList.get(i).id() == id)
				return i;
		}
		
		return -1;
	}

	public void setResource(int row)
	{
		this.rowResId = row;
	}
	
	public void setList(ArrayList<Transaction> trans)
	{
		mOriginalList = trans;
		new TransactionFilter().filter(mConstraint);
		notifyDataSetChanged();
	}
	
	public ArrayList<Transaction> getList()
	{
		return transList;
	}
	
	public void sort()
	{
		Collections.sort(mOriginalList);
		new TransactionFilter().filter(mConstraint);
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
			return;
		
		Transaction trans;
		for (int id : ids)
		{
			trans = Transaction.getTransactionById(id);
			if (trans != null && trans.account == mAcctId)
				mOriginalList.add(trans);
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
		new TransactionFilter().filter(mConstraint);
		notifyDataSetChanged();
	}
	
	public void remove(int[] ids)
	{
		for (int id : ids)
			mOriginalList.remove(getItem(findItemById(id)));
		new TransactionFilter().filter(mConstraint);
		notifyDataSetChanged();
	}
	
	@Override
	public void clear()
	{
		mOriginalList.clear();
		new TransactionFilter().filter(mConstraint);
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
			convertView = mInflater.inflate(rowResId, parent, false);
			
			holder = new ViewHolder();
			holder.check = (CheckBox)convertView.findViewById(R.id.PostedCheckBox);
			holder.date = (TextView)convertView.findViewById(R.id.DateText);
			holder.party = (TextView)convertView.findViewById(R.id.PartyText);
			holder.amount = (TextView)convertView.findViewById(R.id.AmountText);
			
			convertView.setTag(holder);
		}
		else
		{
			holder = (ViewHolder)convertView.getTag();
		}

		// bail early if the transaction doesn't exist, isn't for this account, or is not currently visible
		final Transaction trans = transList.get(position);
		if (trans == null || trans.account == 0)
			return convertView;
		
		final int pos = position;

		// find and retrieve the widgets
		CheckBox postedCheck = holder.check;
		
		if (postedCheck == null)
			return convertView;
		
		postedCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (trans.isPosted() != isChecked)
				{
					boolean budget = trans.budget;
					trans.post(isChecked);
					TransactionActivity ta = (TransactionActivity) context;
					ta.setBalances();
					
					// only need to update the view if it changed from budget to posted
					if (budget)
					{
						ListView lv = ta.getListView();
						View v = lv.getChildAt(pos - lv.getFirstVisiblePosition());
						if (v != null)
						{
							ViewHolder holder = (ViewHolder)v.getTag();
							setViewData(trans, holder, null, null);
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
		String amountStr = nf.format(Math.abs(trans.amount));
		
		if (postedCheck != null)
			postedCheck.setChecked(trans.isPosted());

		// populate the widgets with data
		setViewData(trans, holder, amountStr, dateStr);
		
		return convertView;
	}
	
	private void setViewData(Transaction trans, ViewHolder v, String amountStr, String dateStr)
	{
		String partyStr = "";
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean showColors = prefs.getBoolean("color", false);
		int color = Color.LTGRAY;
		
		if (trans.budget)
		{
			if (trans.type == Transaction.DEPOSIT)
				partyStr += "+";
			else
				partyStr += "-";
		}
		if (trans.type == Transaction.CHECK)
		{
			partyStr += trans.check_num;
			if (trans.budget)
				color = Color.rgb(185, 255, 255);
			else
				color = Color.CYAN;
		}
		else if (trans.type == Transaction.WITHDRAW)
		{
			partyStr += "W";
			if (trans.budget)
				color = Color.rgb(255, 255, 185);
			else
				color = Color.rgb(255, 255, 0);
		}
		else
		{
			partyStr += "D";
			if (trans.budget)
				color = Color.rgb(185, 255, 185);
			else
				color = Color.GREEN;
		}
		partyStr += ":" + trans.party;
		
		TextView dateText = v.date;
		TextView partyText = v.party;
		TextView amountText = v.amount;
		
		if (dateText != null)
		{
			if (dateStr != null)
				dateText.setText(dateStr);
			if (showColors)
				dateText.setTextColor(color);
		}
		if (partyText != null)
		{
			partyText.setText(partyStr);
			if (showColors)
				partyText.setTextColor(color);
		}
		if (amountText != null)
		{
			if (amountStr != null)
				amountText.setText(amountStr);
			if (showColors)
				amountText.setTextColor(color);
		}
	}
	
	static class ViewHolder
	{
		CheckBox check;
		TextView date;
		TextView party;
		TextView amount;
	}

	public class TransactionFilter extends Filter
	{
		public FilterResults filtering(CharSequence constraint)
		{
			return performFiltering(constraint);
		}
		
		@Override
		protected FilterResults performFiltering(CharSequence constraint)
		{
			mConstraint = constraint;
			FilterResults results = new FilterResults();
			
			// do the filtering to decide which items we show
			if (constraint == null || constraint.length() == 0)
			{
				ArrayList<Transaction> list = new ArrayList<Transaction>(mOriginalList);
				results.count = list.size();
				results.values = list;
				return results;
			}
			
			boolean matches = false;
			ArrayList<Transaction> tList = new ArrayList<Transaction>(mOriginalList);
			ArrayList<Transaction> values = new ArrayList<Transaction>();
			results.count = 0;
			results.values = values;
			String[] filters = constraint.toString().split(" ");
			
			for (Transaction trans : tList)
			{
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
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results)
		{
			transList = (ArrayList<Transaction>)results.values;
			notifyDataSetChanged();
		}
	}
}
