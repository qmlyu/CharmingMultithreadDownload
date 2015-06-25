package com.ming.testxutils;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.exception.DbException;
import com.lidroid.xutils.http.HttpHandler;
import com.lidroid.xutils.util.LogUtils;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.OnClick;
import com.ming.testxutils.download.BaseDownloadHolder;
import com.ming.testxutils.download.DownloadInfo;
import com.ming.testxutils.download.DownloadManager;
import com.ming.testxutils.download.DownloadRequestCallBack;
import com.ming.testxutils.download.DownloadService;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * 打开软件主页面
 * @author CharmingLee 2015-6-18
 */
public class MainActivity extends ActionBarActivity {
    @ViewInject(R.id.lv_content)
    private ListView lv_content;
    @ViewInject(R.id.btn_download)
    private Button btn_download;
    @ViewInject(R.id.btn_downloadList)
    private Button btn_downloadList;
    private DownloadManager downloadManager;
    /*下载按钮的下载地址*/
    private String url = "http://p.gdown.baidu.com/f665117435c8bd8a38bcdf3706b41dc5bae9c953ec2461d09f3531b31d8134fb8016e85344cb2e70396f55674062d5639e45562ca1a861b91137e98df30fd4a0e9d23a3c521fb3ee4dd4e14dc8d7931008869a3e3ba617d9af0daf44125bd0703b13ac1f1570c89d137f2d5c223e94b7c0335132cde3b28ea13c1696a41d8482676578de428f8cfcf591690330cbf8eb603e3cfbdf967e6817ff1ea04103a460";
    private MyAdapter adapter;

    @Override
    protected void onRestart() {
        adapter.notifyDataSetChanged();
        super.onRestart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        downloadManager = DownloadService.getDownloadManager(getApplicationContext());
        ViewUtils.inject(this);
        adapter = new MyAdapter();
        lv_content.setAdapter(adapter);
        lv_content.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this, ListViewItemActivity.class);
                intent.putExtra("position", position);
                startActivity(intent);
            }
        });

        btn_downloadList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DownloadList.class);
                startActivity(intent);
            }
        });
    }

    @OnClick(R.id.btn_download)
    public void enterBtn(View view){
        String target = "/sdcard/xUtils/" + System.currentTimeMillis() + "sjzs.apk";
        try {
            downloadManager.addNewDownload(url,"手机助手",target,true,true,new DownloadRequestCallBack());
            adapter.notifyDataSetChanged();
        } catch (DbException e) {
            e.printStackTrace();
        }
    }

    private class MyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return downloadManager.getDownloadInfoListCount();
        }

        @Override
        public Object getItem(int position) {
            return downloadManager.getDownloadInfo(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DownloadInfo downloadInfo = downloadManager.getDownloadInfo(position);
            holdler holder = null;
            if(convertView == null){
                convertView = View.inflate(MainActivity.this, R.layout.download_item, null);
                holder = new holdler(downloadInfo);
                ViewUtils.inject(holder,convertView);
                convertView.setTag(holder);
                holder.refresh();
            }else {
                holder = (MainActivity.holdler) convertView.getTag();
                holder.update(downloadInfo);
            }

            HttpHandler<File> handler = downloadInfo.getHandler();
            if(handler != null){
                DownloadManager.ManagerCallBack requestCallBack = (DownloadManager.ManagerCallBack) handler.getRequestCallBack();
                if(requestCallBack.getBaseCallBack() == null){
                    requestCallBack.setBaseCallBack(new DownloadRequestCallBack());
                }
                requestCallBack.setUserTag(new WeakReference<holdler>(holder));
            }

            return convertView;
        }
    }

    public class holdler extends BaseDownloadHolder {
        @ViewInject(R.id.download_label)
        TextView label;
        @ViewInject(R.id.download_state)
        TextView state;
        @ViewInject(R.id.download_pb)
        ProgressBar progressBar;
        @ViewInject(R.id.download_stop_btn)
        Button stopBtn;
        @ViewInject(R.id.download_remove_btn)
        Button removeBtn;

        public holdler(DownloadInfo downloadInfo) {
            super(downloadInfo);

        }

        @Override
        public void refresh() {
            label.setText(downloadInfo.getFileName()+downloadInfo.getId());
            state.setText(downloadInfo.getState().toString());
            if (downloadInfo.getFileLength() > 0) {
                progressBar.setProgress((int) (downloadInfo.getProgress() * 100 / downloadInfo.getFileLength()));
            } else {
                progressBar.setProgress(0);
            }

            stopBtn.setVisibility(View.VISIBLE);
            stopBtn.setText("暂停");
            HttpHandler.State state = downloadInfo.getState();
            switch (state) {
                case WAITING:
                    stopBtn.setText("暂停");
                    break;
                case STARTED:
                    stopBtn.setText("暂停");
                    break;
                case LOADING:
                    stopBtn.setText("暂停");
                    break;
                case CANCELLED:
                    stopBtn.setText("继续");
                    break;
                case SUCCESS:
                    stopBtn.setVisibility(View.INVISIBLE);
                    break;
                case FAILURE:
                    stopBtn.setText("重试");
                    break;
                default:
                    break;
            }
        }


        @OnClick(R.id.download_remove_btn)
        public void remove(View view) {
            try {
                downloadManager.removeDownload(downloadInfo);
                File file = new File(downloadInfo.getFileSavePath());
                if(file.exists()){
                    file.delete();
                }
                adapter.notifyDataSetChanged();
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
        }

        @OnClick(R.id.download_stop_btn)
        public void stop(View view) {
            HttpHandler.State state = downloadInfo.getState();
            switch (state) {
                case WAITING:
                case STARTED:
                case LOADING:
                    try {
                        downloadManager.stopDownload(downloadInfo);
                    } catch (DbException e) {
                        LogUtils.e(e.getMessage(), e);
                    }
                    break;
                case CANCELLED:
                case FAILURE:
                    try {
                        downloadManager.resumeDownload(downloadInfo, new DownloadRequestCallBack());
                    } catch (DbException e) {
                        LogUtils.e(e.getMessage(), e);
                    }
                    adapter.notifyDataSetChanged();
                    break;
                default:
                    break;
            }
        }

    }

}
