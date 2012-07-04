package org.evergreen.android.accountAccess.holds;

import java.util.Calendar;

import org.evergreen.android.R;
import org.evergreen.android.accountAccess.AccountAccess;
import org.evergreen.android.searchCatalog.RecordInfo;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;

public class PlaceHold extends Activity{

	
	private TextView recipient;
	
	private TextView title;
	
	private TextView author;
	
	private TextView physical_description;
	
	private TextView screen_title;
	
	private AccountAccess accountAccess;
	
	private EditText expiration_date;
	
	private Button placeHold;
	
	private Button cancel;
	
	private DatePickerDialog datePicker = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.place_hold);
		
		RecordInfo record = (RecordInfo) getIntent().getSerializableExtra("recordInfo");
		
		accountAccess = AccountAccess.getAccountAccess();
		
		recipient = (TextView) findViewById(R.id.hold_recipient);
		title = (TextView) findViewById(R.id.hold_title);
		author = (TextView) findViewById(R.id.hold_author);
		physical_description = (TextView) findViewById(R.id.hold_physical_description);
		screen_title = (TextView) findViewById(R.id.header_title);
		cancel = (Button) findViewById(R.id.cancel_hold);
		placeHold = (Button) findViewById(R.id.place_hold);
		expiration_date = (EditText) findViewById(R.id.hold_expiration_date);
		
		screen_title.setText("Place Hold");
		
		recipient.setText(accountAccess.userName);
		title.setText(record.title);
		author.setText(record.author);
		physical_description.setText(record.physical_description);
		
		System.out.println(record.title + " " + record.author);
		
		cancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
		
		final Integer record_id = record.doc_id;
		
		placeHold.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				//accountAccess.createHold(record_id);
				
				accountAccess.getHoldPreCreateInfo(record_id, 4);
			}
		});
		
		
		
		Calendar cal = Calendar.getInstance();
		datePicker = new DatePickerDialog(this,
		         new DatePickerDialog.OnDateSetListener() {
		 
		         public void onDateSet(DatePicker view, int year,
		                                             int monthOfYear, int dayOfMonth)
		         {
		                    Time chosenDate = new Time();
		                    chosenDate.set(dayOfMonth, monthOfYear, year);
		                    long dtDob = chosenDate.toMillis(true);
		                    CharSequence strDate = DateFormat.format("MMMM dd, yyyy", dtDob);
		                    expiration_date.setText(strDate);
		                    //set current date          
		        }}, cal.get(Calendar.YEAR),cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
	
		expiration_date.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				datePicker.show();
			}
		});
		
	}
	
}
