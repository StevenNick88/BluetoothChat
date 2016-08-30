/**
 * 蓝牙聊天项目
 * 搜索和连接蓝牙设备
 */
package com.gxu.bluetoothchat;

import java.util.Set;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class DeviceListActivity extends Activity
{

	private static final String TAG = "DeviceListActivity";
	private static final boolean D = true;

	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter mBtAdapter;
	private ArrayAdapter<String> mPairedDevicesArrayAdapter;
	private ArrayAdapter<String> mNewDevicesArrayAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		//为标题栏加上加上圆形进度条
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);
		//设置关闭当前Activity的返回值（如果用户通过按下硬件返回键关闭Activity，或者在调用finish以前没有调用setResult，那么
		//结果码将被设为RESULT_CANCELED，结果Intent将被设为null。）
		setResult(Activity.RESULT_CANCELED);
		//初始化搜索按钮
		Button scanButton = (Button) findViewById(R.id.button_scan);
		//扫描按钮监听器
		scanButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				//扫描
				doDiscovery();
				//扫描之后影藏扫描按钮
				v.setVisibility(View.GONE);
			}
		});
		//初始化用于保存已配对的蓝牙设备的ArrayAdapter对象
		mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);
		//初始化用于保存新搜索到的蓝牙设备的ArrayAdapter对象
		mNewDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);

		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(mPairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);
		//注册用于接收已搜索到的蓝牙设备的Receiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);
		//注册搜索完成的接收器
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);

		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		//获得当前已配对的设备：用set集合保存
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
		//如果当前有已经配对的设备，将其显示在列表中
		if (pairedDevices.size() > 0)
		{
			findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
			for (BluetoothDevice device : pairedDevices)
			{
				mPairedDevicesArrayAdapter.add(device.getName() + "\n"
						+ device.getAddress());
			}
		}
		//如果当前没有已经配对的设备
		else
		{
			String noDevices = getResources().getText(R.string.none_paired)
					.toString();
			mPairedDevicesArrayAdapter.add(noDevices);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if (mBtAdapter != null)
		{
			mBtAdapter.cancelDiscovery();
		}
		this.unregisterReceiver(mReceiver);
	}
	//扫描
	private void doDiscovery()
	{
		if (D)
			Log.d(TAG, "doDiscovery()");
		//设置圆形进度条可见
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.scanning);

		findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
		//如果正在扫描，则停止，重新开始扫描
		if (mBtAdapter.isDiscovering())
		{
			mBtAdapter.cancelDiscovery();
		}
		mBtAdapter.startDiscovery();
	}
	//设置列表项的点击事件
	private OnItemClickListener mDeviceClickListener = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3)
		{
			mBtAdapter.cancelDiscovery();

			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
			//如果一个activity要返回数据到启动它的那个activity，可以调用setResult()方法。
			//调用setResult()方法必须在finish()之前。
			setResult(Activity.RESULT_OK, intent);
			
			finish();
		}
	};
	//设置广播接收器（用于处理搜索到的蓝牙设备以及搜索完成的动作）
	private final BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			//每发现一个设备，会执行下面的代码
			if (BluetoothDevice.ACTION_FOUND.equals(action))
			{
				//获得BluetoothDevice对象
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				//如果该设备已经配对，则忽略该设备
				if (device.getBondState() != BluetoothDevice.BOND_BONDED)
				{
					//设备未配对，将其添加到mNewDevicesArrayAdapter对象中，以便可显示在列表中
					mNewDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
			}
			//搜索完成时执行下面的代码
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				//搜索完成，设置圆形进度条不可见
				setProgressBarIndeterminateVisibility(false);
				setTitle(R.string.select_device);
				//如果未搜索到任何设备，在当前界面显示提示信息
				if (mNewDevicesArrayAdapter.getCount() == 0)
				{
					String noDevices = getResources().getText(
							R.string.none_found).toString();
					//将提示信息“未发现蓝牙设备”添加到适配器中
					mNewDevicesArrayAdapter.add(noDevices);
				}
			}
		}
	};

}
