package com.ming.testxutils;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.exception.DbException;
import com.lidroid.xutils.http.HttpHandler;
import com.lidroid.xutils.util.LogUtils;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.OnClick;
import com.ming.testxutils.Utils.MyLog;
import com.ming.testxutils.download.DataChanger;
import com.ming.testxutils.download.DownloadInfo;
import com.ming.testxutils.download.DownloadManager;
import com.ming.testxutils.download.DownloadRequestCallBack;
import com.ming.testxutils.download.DownloadService;
import com.ming.testxutils.download.Watcher;

/**
 * 点击主页item后弹出的页面
 * @author CharmingLee 2015-6-18
 */
public class ListViewItemActivity extends ActionBarActivity {
    @ViewInject(R.id.download_label)
    private TextView label;
    @ViewInject(R.id.download_state)
    private TextView state;
    @ViewInject(R.id.download_pb)
    private ProgressBar progressBar;
    @ViewInject(R.id.download_remove_btn)
    private Button removeBtn;
    private DownloadManager downloadManager;
    private int index = -1;
    private DownloadInfo downloadInfo;

    private Watcher watcher = new Watcher() {

        @Override
        public void ontifyDownloadDataChange() {
                downloadInfo = downloadManager.getDownloadInfo(index);
                if (label != null) label.setText(downloadInfo.getFileName());
                if (state != null) state.setText(downloadInfo.getState().name());
                if (progressBar != null) progressBar.setProgress((int) (downloadInfo.getProgress() * 100 / downloadInfo.getFileLength()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_view_item);

        downloadManager = DownloadService.getDownloadManager(this);
        ViewUtils.inject(this);
        index = (int) getIntent().getExtras().get("position");
        downloadInfo = downloadManager.getDownloadInfo(index);
        MyLog.d(downloadInfo.getState().name());
        if (label != null) label.setText(downloadInfo.getFileName());
        if (state != null) state.setText(downloadInfo.getState().name());
        if (progressBar != null) progressBar.setProgress((int) (downloadInfo.getFileLength() == 0 ?0:downloadInfo.getProgress() * 100 / downloadInfo.getFileLength()));

        DataChanger.getInstance().addObserver(watcher);
    }

    @OnClick(R.id.download_remove_btn)
    public void remove(View view) {
        if (downloadInfo != null) {
            try {
                downloadManager.removeDownload(downloadInfo);
                if (progressBar != null) progressBar.setProgress(0);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
        }
    }

    @OnClick(R.id.download_stop_btn)
    public void stop(View view) {
        if (downloadInfo != null) {
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
                    break;
                default:
                    break;
            }
        }
    }

}
