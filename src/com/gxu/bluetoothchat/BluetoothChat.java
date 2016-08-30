/**
 * 蓝牙聊天项目（像网路Socket一样，服务端通过accept方法获得 BluetoothSocket对象，
 * 并获得InputStream和OutputStream来接收和发送数据，客户端则使用OutputStream向服务端发送数据）
 * 蓝牙聊天的主界面类
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
	//相关变量、常量、GUI组件定义
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
		//设置定制标题栏的布局,在标题栏的右侧显示蓝牙设备的状态
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.bluetooth_chat);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);
		//设置标题栏上显示的内容
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mdeviceState = (TextView) findViewById(R.id.title_right_text);
		//获得蓝牙适配器
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		//检测蓝牙设备是否开启		如果mBluetoothAdapter为null，说明当前手机没有蓝牙模块
		if (mBluetoothAdapter == null)
		{
			Toast.makeText(this, "当前手机不支持蓝牙...",
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
		//如果蓝牙是关闭的，打开蓝牙（mBluetoothAdapter.isEnabled方法返回true，如果蓝牙当前已启用并准备投入使用）
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

	//设置聊天面板并发送聊天消息
	private void setupChat()
	{
		Log.d(TAG, "setupChat()");

		mConversationArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.message);
		//获得聊天面板的引用
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
	//使本机蓝牙处于可见(即处于易被搜索到状态)，便于其他设备发现本机蓝牙  		用于菜单事件的调用
	private void ensureDiscoverable()
	{
		if (FLAG)
			Log.d(TAG, "ensure discoverable");
		//如果蓝牙的扫描模式不是处于易被搜索到状态
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
		{
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			//使本机蓝牙在300秒内可被搜索  
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	private void sendMessage(String message)
	{
		//在发送消息之前核对蓝牙设备是否连接成功
		if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED)
		{
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
			return;
		}
		//检查文本输入框中是否有文本
		if (message.length() > 0)
		{
			//获得发送消息的字节形式的数据
			byte[] send = message.getBytes();
			mChatService.write(send);
			mOutStringBuffer.setLength(0);
			mOutEditText.setText(mOutStringBuffer);
		}
	}
	//EditText中的文本监听器
	private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener()
	{
		/**
		 * 文本改变时触发
		 * 参数
			v	被点击的视图。
			ACTIONID	标识符的动作。 这将是要么你提供的标识符，或EditorInfo.IME_NULL如果被调用由于键被按下回车。
			事件	如果通过一个输入键触发，这是事件; 否则，此为null。
		 */
		public boolean onEditorAction(TextView view, int actionId,
				KeyEvent event)
		{
			//当在你按下键盘上的“回车”键时，就会sendMessage(message)
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
	 * Handler消息的处理者。通过Handler对象我们可以封装Message对象，然后通过sendMessage(msg)把Message对象添加到
	 * MessageQueue中;当MessageQueue循环到该Message时，就会调用该Message对象对应的handler对象的handleMessage()
	 * 方法对其进行处理。由于是在handleMessage()方法中处理消息，因此我们应该编写一个类继承自Handler，然后在handleMessage()
	 * 处理我们需要的操作。
	 */
	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			// 根据what字段判断是哪个消息
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
			//打开蓝牙的请求码
		case REQUEST_ENABLE_BT:
			//打开蓝牙成功
			if (resultCode == Activity.RESULT_OK)
			{
				//设置聊天面板并发送聊天消息
				setupChat();
			}
			//打开蓝牙失败
			else
			{
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	//添加选项菜单：连接蓝牙设备、可被发现
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		//自定义菜单的背景颜色
		setMenuBackground(); 
		return true;
	}

	//设置menu菜单的背景
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
//                                    view.setBackgroundResource( R.drawable.menu_backg);//设置背景图片
                                    view.setBackgroundColor(new Color().RED);//设置背景色
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

	//监听菜单
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		//连接蓝牙设备
		case R.id.scan:
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
			//蓝牙设备可被发现
		case R.id.discoverable:
			ensureDiscoverable();
			return true;
		}
		return false;
	}

}