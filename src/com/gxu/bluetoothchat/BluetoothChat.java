/**
 * ����������Ŀ������·Socketһ���������ͨ��accept������� BluetoothSocket����
 * �����InputStream��OutputStream�����պͷ������ݣ��ͻ�����ʹ��OutputStream�����˷������ݣ�
 * �����������������
 */
package com.gxu.bluetoothchat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InflateException;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.LayoutInflater.Factory;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothChat extends Activity
{
	//��ر�����������GUI�������
	private static final String TAG = "BluetoothChat";
	private static final boolean FLAG = true;

	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	private TextView mTitle;
	private TextView mdeviceState;
	private ListView mConversationView;
	private EditText mOutEditText;
	private Button mSendButton;

	private String mConnectedDeviceName = null;
	private ArrayAdapter<String> mConversationArrayAdapter;
	private StringBuffer mOutStringBuffer;
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothChatService mChatService = null;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (FLAG)
			Log.e(TAG, "+++ ON CREATE +++");
		//���ö��Ʊ������Ĳ���,�ڱ��������Ҳ���ʾ�����豸��״̬
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.bluetooth_chat);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);
		//���ñ���������ʾ������
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mdeviceState = (TextView) findViewById(R.id.title_right_text);
		//�������������
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		//��������豸�Ƿ���		���mBluetoothAdapterΪnull��˵����ǰ�ֻ�û������ģ��
		if (mBluetoothAdapter == null)
		{
			Toast.makeText(this, "��ǰ�ֻ���֧������...",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();
		if (FLAG)
			Log.e(TAG, "++ ON START ++");
		//��������ǹرյģ���������mBluetoothAdapter.isEnabled��������true�����������ǰ�����ò�׼��Ͷ��ʹ�ã�
		if (!mBluetoothAdapter.isEnabled())
		{
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
		else
		{
			if (mChatService == null)
				setupChat();
		}
	}

	@Override
	public synchronized void onResume()
	{
		super.onResume();
		if (FLAG)
			Log.e(TAG, "+ ON RESUME +");

		if (mChatService != null)
		{
			if (mChatService.getState() == BluetoothChatService.STATE_NONE)
			{
				mChatService.start();
			}
		}
	}

	//����������岢����������Ϣ
	private void setupChat()
	{
		Log.d(TAG, "setupChat()");

		mConversationArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.message);
		//���������������
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

		mOutEditText = (EditText) findViewById(R.id.edit_text_out);
		mOutEditText.setOnEditorActionListener(mWriteListener);

		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				String message = view.getText().toString();
				sendMessage(message);
			}
		});

		mChatService = new BluetoothChatService(this, mHandler);

		mOutStringBuffer = new StringBuffer("");
	}

	@Override
	public synchronized void onPause()
	{
		super.onPause();
		if (FLAG)
			Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop()
	{
		super.onStop();
		if (FLAG)
			Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (mChatService != null)
			mChatService.stop();
		if (FLAG)
			Log.e(TAG, "--- ON DESTROY ---");
	}
	//ʹ�����������ڿɼ�(�������ױ�������״̬)�����������豸���ֱ�������  		���ڲ˵��¼��ĵ���
	private void ensureDiscoverable()
	{
		if (FLAG)
			Log.d(TAG, "ensure discoverable");
		//���������ɨ��ģʽ���Ǵ����ױ�������״̬
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
		{
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			//ʹ����������300���ڿɱ�����  
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	private void sendMessage(String message)
	{
		//�ڷ�����Ϣ֮ǰ�˶������豸�Ƿ����ӳɹ�
		if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED)
		{
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
			return;
		}
		//����ı���������Ƿ����ı�
		if (message.length() > 0)
		{
			//��÷�����Ϣ���ֽ���ʽ������
			byte[] send = message.getBytes();
			mChatService.write(send);
			mOutStringBuffer.setLength(0);
			mOutEditText.setText(mOutStringBuffer);
		}
	}
	//EditText�е��ı�������
	private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener()
	{
		/**
		 * �ı��ı�ʱ����
		 * ����
			v	���������ͼ��
			ACTIONID	��ʶ���Ķ����� �⽫��Ҫô���ṩ�ı�ʶ������EditorInfo.IME_NULL������������ڼ������»س���
			�¼�	���ͨ��һ������������������¼�; ���򣬴�Ϊnull��
		 */
		public boolean onEditorAction(TextView view, int actionId,
				KeyEvent event)
		{
			//�����㰴�¼����ϵġ��س�����ʱ���ͻ�sendMessage(message)
			if (actionId == EditorInfo.IME_NULL
					&& event.getAction() == KeyEvent.ACTION_UP)
			{
				String message = view.getText().toString();
				sendMessage(message);
			}
			if (FLAG)
				Log.i(TAG, "END onEditorAction");
			return true;
		}
	};
	/**
	 * Handler��Ϣ�Ĵ����ߡ�ͨ��Handler�������ǿ��Է�װMessage����Ȼ��ͨ��sendMessage(msg)��Message������ӵ�
	 * MessageQueue��;��MessageQueueѭ������Messageʱ���ͻ���ø�Message�����Ӧ��handler�����handleMessage()
	 * ����������д�����������handleMessage()�����д�����Ϣ���������Ӧ�ñ�дһ����̳���Handler��Ȼ����handleMessage()
	 * ����������Ҫ�Ĳ�����
	 */
	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			// ����what�ֶ��ж����ĸ���Ϣ
			switch (msg.what)
			{
			case MESSAGE_STATE_CHANGE:
				if (FLAG)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1)
				{
				case BluetoothChatService.STATE_CONNECTED:
					mdeviceState.setText(R.string.title_connected_to);
					mdeviceState.append(mConnectedDeviceName);
					mConversationArrayAdapter.clear();
					break;
				case BluetoothChatService.STATE_CONNECTING:
					mdeviceState.setText(R.string.title_connecting);
					break;
				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
					mdeviceState.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				String writeMessage = new String(writeBuf);
				mConversationArrayAdapter.add("Me:  " + writeMessage);
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				String readMessage = new String(readBuf, 0, msg.arg1);
				mConversationArrayAdapter.add(mConnectedDeviceName + ":  "
						+ readMessage);
				break;
			case MESSAGE_DEVICE_NAME:
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (FLAG)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode)
		{
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK)
			{
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);

				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				mChatService.connect(device);
			}
			break;
			//��������������
		case REQUEST_ENABLE_BT:
			//�������ɹ�
			if (resultCode == Activity.RESULT_OK)
			{
				//����������岢����������Ϣ
				setupChat();
			}
			//������ʧ��
			else
			{
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	//���ѡ��˵������������豸���ɱ�����
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		//�Զ���˵��ı�����ɫ
		setMenuBackground(); 
		return true;
	}

	//����menu�˵��ı���
    protected void setMenuBackground(){
            
            Log.d(TAG, "Enterting setMenuBackGround");
            getLayoutInflater().setFactory( new Factory() {
                
                @Override
                public View onCreateView ( String name, Context context, AttributeSet attrs ) {
                 
                    if ( name.equalsIgnoreCase( "com.android.internal.view.menu.IconMenuItemView" ) ) {
                        
                        try { // Ask our inflater to create the view
                            LayoutInflater f = getLayoutInflater();
                            final View view = f.createView( name, null, attrs );
                            new Handler().post( new Runnable() {
                                public void run () {
//                                    view.setBackgroundResource( R.drawable.menu_backg);//���ñ���ͼƬ
                                    view.setBackgroundColor(new Color().RED);//���ñ���ɫ
                                }
                            } );
                            return view;
                        }
                        catch ( InflateException e ) {}
                        catch ( ClassNotFoundException e ) {}
                    }
                    return null;
                }
            });
    }

	//�����˵�
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		//���������豸
		case R.id.scan:
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
			//�����豸�ɱ�����
		case R.id.discoverable:
			ensureDiscoverable();
			return true;
		}
		return false;
	}

}