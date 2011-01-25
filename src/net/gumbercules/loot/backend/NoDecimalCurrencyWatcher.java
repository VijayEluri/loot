package net.gumbercules.loot.backend;

import java.util.ArrayList;
import java.util.Arrays;

import android.text.Editable;
import android.util.Log;

public class NoDecimalCurrencyWatcher extends CurrencyWatcher
{
	private static final String TAG			= "net.gumbercules.loot.backend.CurrencyWatcher";
	
	public NoDecimalCurrencyWatcher()
	{
		super();
		
		mAccepted = new Character[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
	}
	
	public char[] getAcceptedChars()
	{
		int len = mAccepted.length;
		char[] accepted = new char[len];
		for (int i = len - 1; i >= 0; --i)
		{
			accepted[i] = mAccepted[i];
		}
		return accepted;
	}

	@Override
	public void afterTextChanged(Editable s)
	{
		String str = s.toString();
		if (mChanged)
		{
			mChanged = false;
			mOld = str;
			return;
		}
		
		final ArrayList<Character> accepted = new ArrayList<Character>(Arrays.asList(mAccepted));
		int pos = 0;
		for (char c : str.toCharArray())
		{
			if (!accepted.contains(c))
			{
				s.replace(pos, pos + 1, "", 0, 0);
				Log.i(TAG + ".afterTextChanged()", "Input rejected because of invalid character: " + c);
				return;
			}
			++pos;
		}
		
		int index = 0;
		while (index < str.length() && str.charAt(index) == '0')
		{
			++index;
		}
		str = str.substring(index);
		str = str.replace(String.valueOf(mSeparator), ""); // remove the separator for now
		final int min_length = mFractionDigits + 1; // number of fractional digits + one digit to the left of separator
		while (str.length() < min_length)
		{
			str = "0" + str;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(str.substring(0, str.length() - 2))
			.append(mSeparator)
			.append(str.substring(str.length() - 2));
		
		str = sb.toString();
		mChanged = true;
		s.replace(0, s.length(), str);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after)
	{
		super.beforeTextChanged(s, start, count, after);
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		super.onTextChanged(s, start, before, count);
	}
}
