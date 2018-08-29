package net.bsn.resaa.hybridcalltest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import net.bsn.resaa.hybridcall.calloperators.CallOperator;
import net.bsn.resaa.hybridcall.calloperators.GsmCallOperator;
import net.bsn.resaa.hybridcall.calloperators.VoipCallOperator;
import net.bsn.resaa.hybridcall.internet.voip.TransferLayerProtocol;
import net.bsn.resaa.hybridcall.internet.voip.VoipException;
import net.bsn.resaa.hybridcall.internet.voip.VoipManager;
import net.bsn.resaa.hybridcall.internet.voip.VoipProfile;
import net.bsn.resaa.hybridcall.utilities.Func;
import net.bsn.resaa.hybridcall.utilities.logging.Logging;
import net.bsn.resaa.hybridcalltest.utilities.FileLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

	private static final int CONTACT_PICK_REQUEST_CODE = 1;

//	private boolean callRequested = false;

	CallOperator activeOperator;
	List<CallOperator> possibleOperators;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Logging.setLogger(new FileLogger(getString(R.string.app_name), getResources().getInteger(R.integer.logging_max_batch_size)));

		Logging.info("Activity created.");

		requestPermissions();

		findPossibleOperators();

		initializeViews();
	}

	@Override
	protected void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		handleIntent(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		if (intent.getData() != null) {
			((TextView) findViewById(R.id.phone_number_text_view)).setText(intent.getData().getSchemeSpecificPart());
			if (Intent.ACTION_CALL.equals(intent.getAction())) {
				final Handler handler = new Handler();
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						(findViewById(R.id.call_button)).callOnClick();
					}
				}, 100);
			}
		}
	}

	@Override
	protected void onDestroy() {
		for (CallOperator operator : possibleOperators)
			operator.destroy();
		LinphoneVoipManager.destroy();
		super.onDestroy();
	}

	private void findPossibleOperators() {
		possibleOperators = new ArrayList<>();

		VoipManager voipManager = LinphoneVoipManager.createAndStart(this);
//		VoipManager voipManager = new TestVoipManager(this);
		VoipProfile profile = new VoipProfile(getResources().getString(R.string.sip_profile_username),
				getResources().getString(R.string.sip_server_url));
		try {
			voipManager.registerCaller(profile, getResources().getString(R.string.sip_profile_password),
					getResources().getInteger(R.integer.sip_server_port),
					TransferLayerProtocol.valueOf(getResources().getString(R.string.sip_call_protocol).toUpperCase()),
					getResources().getInteger(R.integer.sip_profile_expires_in),
					getResources().getInteger(R.integer.sip_profile_retry_in));
		} catch (VoipException e) {
			Toast.makeText(this, R.string.register_error, Toast.LENGTH_LONG).show();
		}

		possibleOperators.add(GsmCallOperator.getInstance(this));
		possibleOperators.add(new VoipCallOperator(this, voipManager));
	}

	private List<CallOperator> getAvailableOperators() {
		List<CallOperator> operators = new ArrayList<>();
		for (CallOperator operator : possibleOperators)
			if (operator.isAvailable())
				operators.add(operator);
		return operators;
	}


	private CallOperator getBestOperator() {
		Logging.info("Searching for best operator.");

		List<CallOperator> operators = getAvailableOperators();

		Collections.sort(operators, new Comparator<CallOperator>() {
			@Override
			public int compare(CallOperator callOperator, CallOperator t1) {
				return Integer.valueOf(getOperatorEfficiencyNumber(callOperator))
						.compareTo(getOperatorEfficiencyNumber(t1));
			}
		});

		Logging.info("Best operator is " + operators.get(operators.size() - 1).getClass().getSimpleName() + ".");

		return operators.get(operators.size() - 1);
	}


	private static int getOperatorEfficiencyNumber(CallOperator operator) {
		CallOperator.CallQuality quality = operator.getCurrentQuality();
		CallOperator.CallPrice price = operator.getCurrentPrice();

		if (quality == CallOperator.CallQuality.PERFECT && price == CallOperator.CallPrice.CHEAP)
			return 9;
		if (quality == CallOperator.CallQuality.PERFECT && price == CallOperator.CallPrice.MEDIOCRE)
			return 8;
		if (quality == CallOperator.CallQuality.MEDIOCRE && price == CallOperator.CallPrice.CHEAP)
			return 7;
		if (quality == CallOperator.CallQuality.PERFECT && price == CallOperator.CallPrice.EXPENSIVE)
			return 6;
		if (quality == CallOperator.CallQuality.MEDIOCRE && price == CallOperator.CallPrice.MEDIOCRE)
			return 5;
		if (quality == CallOperator.CallQuality.MEDIOCRE && price == CallOperator.CallPrice.EXPENSIVE)
			return 4;
		if (quality == CallOperator.CallQuality.POOR && price == CallOperator.CallPrice.CHEAP)
			return 3;
		if (quality == CallOperator.CallQuality.POOR && price == CallOperator.CallPrice.MEDIOCRE)
			return 2;
		if (quality == CallOperator.CallQuality.POOR && price == CallOperator.CallPrice.EXPENSIVE)
			return 1;
		return 0;
	}

	private void requestPermissions() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1)
			ActivityCompat.requestPermissions(this, new String[]{
					Manifest.permission.ACCESS_NETWORK_STATE,
					Manifest.permission.ACCESS_WIFI_STATE,
					Manifest.permission.INTERNET,
					Manifest.permission.CALL_PHONE,
					Manifest.permission.READ_PHONE_STATE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.MODIFY_AUDIO_SETTINGS,
					Manifest.permission.WAKE_LOCK,
					Manifest.permission.MODIFY_PHONE_STATE}, 0);
	}

	private void initializeViews() {
		final FrameLayout surveyHolder = findViewById(R.id.survey_holder);
		final FrameLayout loadingHolder = findViewById(R.id.loading_holder);
		final TextView phoneNumberTextView = findViewById(R.id.phone_number_text_view);
		final Dictionary<String, Character> nameCharMap = new Hashtable<>();
		final RatingBar callQualityRatingBar = findViewById(R.id.call_quality_rating_bar);

		for (int i = 0; i <= 9; i++)
			nameCharMap.put("" + i, (char) ('0' + i));
		nameCharMap.put("star", '✱');
		nameCharMap.put("hashtag", '#');

		for (final String key : Collections.list(nameCharMap.keys())) {
			findViewById(this.getResources()
					.getIdentifier("number_" + key + "_button", "id", this.getPackageName()))
					.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							phoneNumberTextView.setText(phoneNumberTextView.getText() + nameCharMap.get(key).toString());
							if (activeOperator != null)
								activeOperator.sendDtmf(nameCharMap.get(key));
						}
					});
		}

		final ImageButton deleteButton = findViewById(R.id.delete_button);
		deleteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (phoneNumberTextView.getText().length() > 0)
					phoneNumberTextView.setText(phoneNumberTextView.getText()
							.subSequence(0, phoneNumberTextView.getText().length() - 1));
			}
		});

		final ImageButton endCallButton = findViewById(R.id.end_call_button);
		endCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				activeOperator.endCall();
			}
		});

		final ImageButton contactButton = findViewById(R.id.contact_button);
		contactButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
				intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
				startActivityForResult(intent, CONTACT_PICK_REQUEST_CODE);
			}
		});

		final ImageButton callButton = findViewById(R.id.call_button);

		callButton.setOnClickListener(new View.OnClickListener() {
			@SuppressLint({"StaticFieldLeak", "MissingPermission"})
			@Override
			public void onClick(View view) {
				// inefficient as hell! change it when creating SDK
				new AsyncTask<Void, Void, CallOperator>() {
					@Override
					protected void onPreExecute() {
						super.onPreExecute();
						loadingHolder.setVisibility(View.VISIBLE);
					}

					@Override
					protected void onPostExecute(final CallOperator operator) {
						super.onPostExecute(operator);

						final Func<String, Void> onCallStartedListener = new Func<String, Void>() {
							@Override
							public Void run(String s) {
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										contactButton.setVisibility(View.GONE);
										deleteButton.setVisibility(View.GONE);
										callButton.setVisibility(View.GONE);
										endCallButton.setVisibility(View.VISIBLE);
										activeOperator = operator;
									}
								});
								return null;
							}
						};

						final Func<String, Void> onCallEndedListener = new Func<String, Void>() {
							@Override
							public Void run(String s) {
								final Func<String, Void> _this = this;
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										surveyHolder.setVisibility(View.VISIBLE);
										contactButton.setVisibility(View.VISIBLE);
										deleteButton.setVisibility(View.VISIBLE);
										callButton.setVisibility(View.VISIBLE);
										endCallButton.setVisibility(View.GONE);
										activeOperator.removeOnCallStartedListener(onCallStartedListener);
										activeOperator.removeOnCallEndedListener(_this);
										activeOperator = null;
//						callRequested = false;

									}
								});
								return null;
							}
						};

						operator.addOnCallStartedListener(onCallStartedListener);
						operator.addOnCallEndedListener(onCallEndedListener);

						loadingHolder.setVisibility(View.GONE);
						operator.call(phoneNumberTextView.getText().toString()
								.replaceAll(" ", "").replaceAll("\\+98", "0"));
//								callRequested = true;
					}

					@Override
					protected CallOperator doInBackground(Void... voids) {
						CallOperator tmp = getBestOperator();
						Logging.info("Finished evaluating operators.");

						List<CallOperator> operators = getAvailableOperators();
						CallOperator operator = null;

						if (operators.size() <= 1)
							operator = operators.get(0);
						boolean chooseInternet = new Random().nextInt(100) <= getResources().getInteger(R.integer.internet_call_percent);
						for (CallOperator op : operators) {
							if (VoipCallOperator.class.isInstance(op) && chooseInternet) {
								operator = op;
								break;
							}
							if (GsmCallOperator.class.isInstance(op) && !chooseInternet) {
								operator = op;
								break;
							}
						}

						Logging.info("Picked " + operator.getClass().getSimpleName());

						return operator;
					}
				}.execute();
			}
		});

		Button callQualityRatingButton = findViewById(R.id.call_quality_rating_button);
		callQualityRatingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				surveyHolder.setVisibility(View.GONE);
				Logging.info("Call quality rating is " + callQualityRatingBar.getRating() + ".");
			}
		});

		Button callQualitySkipRatingButton = findViewById(R.id.call_quality_skip_rating_button);
		callQualitySkipRatingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				surveyHolder.setVisibility(View.GONE);
				Logging.info("Call quality rating is skipped.");
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CONTACT_PICK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			Uri contactUri = data.getData();
			String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
			Cursor cursor = getContentResolver().query(contactUri, projection,
					null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
				String number = cursor.getString(numberIndex);
				TextView phoneNumberTextView = findViewById(R.id.phone_number_text_view);
				phoneNumberTextView.setText(number);
			}
			cursor.close();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		((FileLogger) Logging.getLogger()).flush();
//		if (callRequested) {
//			new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//				@Override
//				public void run() {
//
//					Intent sendIntent = new Intent(MainActivity.this, MainActivity.class);
//					sendIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//					startActivity(sendIntent);
//				}
//			}, 500);
//			callRequested = false;
//		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		((FileLogger) Logging.getLogger()).flush();
		finish();
		System.exit(0);
	}
}