package com.ming.testxutils.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.lidroid.xutils.DbUtils;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.db.converter.ColumnConverter;
import com.lidroid.xutils.db.converter.ColumnConverterFactory;
import com.lidroid.xutils.db.sqlite.ColumnDbType;
import com.lidroid.xutils.db.sqlite.Selector;
import com.lidroid.xutils.exception.DbException;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.HttpHandler;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.util.LogUtils;
import com.ming.testxutils.DownloadList;
import com.ming.testxutils.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: wyouflf
 * Date: 13-11-10
 * Time: 下午8:10
 */
public class DownloadManager {
    private List<DownloadInfo> downloadInfoList;
    private NotificationManager mNotificationManager;//Notification管理
    private Notification notification;
    private NotificationCompat.Builder mBuilder;
    private int maxDownloadThread = 3;
    private Context mContext;
    private DbUtils db;

    /*package*/
    DownloadManager(Context appContext) {
        ColumnConverterFactory.registerColumnConverter(HttpHandler.State.class, new HttpHandlerStateConverter());
        mContext = appContext;
        db = DbUtils.create(mContext);
        try {
            downloadInfoList = db.findAll(Selector.from(DownloadInfo.class));
        } catch (DbException e) {
            LogUtils.e(e.getMessage(), e);
        }
        if (downloadInfoList == null) {
            downloadInfoList = new ArrayList<DownloadInfo>();
        }
        if(mNotificationManager == null){
            mNotificationManager = (NotificationManager) mContext.getSystemService(mContext.NOTIFICATION_SERVICE);
        }
    }

    public int getDownloadInfoListCount() {
        return downloadInfoList.size();
    }

    public DownloadInfo getDownloadInfo(int index) {
        return downloadInfoList.get(index);
    }
    /**
     * 添加新的下载任务，并加入到下载队列
     * @param url 下载的UIL
     * @param fileName 文件名
     * @param target 保存路径 示例：String target = "/sdcard/xUtils/" + "lzfile.apk";
     * @param autoResume 如果目标文件存在，接着未完成的部分继续下载。服务器不支持RANGE时将从新下载。
     * @param autoRename 如果从请求返回信息中获取到文件名，下载完成后自动重命名。
     * @param callback 下载回调
     * @throws DbException
     */
    public synchronized void addNewDownload(String url, String fileName, String target,
                               boolean autoResume, boolean autoRename,
                               final RequestCallBack<File> callback) throws DbException {
        final DownloadInfo downloadInfo = new DownloadInfo();
        downloadInfo.setDownloadUrl(url);
        downloadInfo.setAutoRename(autoRename);
        downloadInfo.setAutoResume(autoResume);
        downloadInfo.setFileName(fileName);
        downloadInfo.setFileSavePath(target);
        HttpUtils http = new HttpUtils();
        http.configRequestThreadPoolSize(maxDownloadThread);
        HttpHandler<File> handler = http.download(url, target, autoResume, autoRename, new ManagerCallBack(downloadInfo, callback));
        downloadInfo.setHandler(handler);
        downloadInfo.setState(handler.getState());
        downloadInfoList.add(downloadInfo);
        db.saveBindingId(downloadInfo);

        //=========创建通知栏===========
        //如果构造器为空，则创建出来
        if(mBuilder == null){
            mBuilder = createNotificationBuilder(downloadInfo);
        }
    }

    /**
     * 创建通知栏构造器
     * @param downloadInfo
     * @return
     */
    private NotificationCompat.Builder createNotificationBuilder(final DownloadInfo downloadInfo) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
        builder.setWhen(System.currentTimeMillis())// 通知产生的时间，会在通知信息里显示
                .setContentIntent(PendingIntent.getActivity(mContext,1,new Intent(),0))
                .setPriority(Notification.PRIORITY_DEFAULT)// 设置该通知优先级
                .setOngoing(false)
                .setContentIntent(getPendingIntent(false,downloadInfo))
                .setSmallIcon(R.drawable.a1);
        return builder;
    }

    /**
     * 通知栏点击回调事件
     * @param isDownloadFinish 是否下载完成
     * @param downloadInfo 下载对象
     * @return
     */
    private PendingIntent getPendingIntent(boolean isDownloadFinish,DownloadInfo downloadInfo) {
        Intent intent = null;
        if(isDownloadFinish){//下载完成打开安装界面
            intent=new Intent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(android.content.Intent.ACTION_VIEW);
            File file = new File(downloadInfo.getFileSavePath());
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }else{//下载中打开下载列表
            intent = new Intent();
            intent.setClass(mContext,DownloadList.class);
        }
        return PendingIntent.getActivity(mContext, 0, intent, 0);
    }

    public void resumeDownload(int index, final RequestCallBack<File> callback) throws DbException {
        final DownloadInfo downloadInfo = downloadInfoList.get(index);
        resumeDownload(downloadInfo, callback);
    }

    public synchronized void resumeDownload(DownloadInfo downloadInfo, final RequestCallBack<File> callback) throws DbException {
        HttpUtils http = new HttpUtils();
        http.configRequestThreadPoolSize(maxDownloadThread);
        HttpHandler<File> handler = http.download(
                downloadInfo.getDownloadUrl(),
                downloadInfo.getFileSavePath(),
                downloadInfo.isAutoResume(),
                downloadInfo.isAutoRename(),
                new ManagerCallBack(downloadInfo, callback));
        downloadInfo.setHandler(handler);
        downloadInfo.setState(handler.getState());
        db.saveOrUpdate(downloadInfo);
    }

    public void removeDownload(int index) throws DbException {
        DownloadInfo downloadInfo = downloadInfoList.get(index);
        removeDownload(downloadInfo);
    }

    public synchronized void removeDownload(DownloadInfo downloadInfo) throws DbException {
        HttpHandler<File> handler = downloadInfo.getHandler();
        if (handler != null && !handler.isCancelled()) {
            handler.cancel();
        }
        downloadInfoList.remove(downloadInfo);
        db.delete(downloadInfo);
        mNotificationManager.cancel((int) downloadInfo.getId());
    }

    public synchronized void stopDownload(int index) throws DbException {
        DownloadInfo downloadInfo = downloadInfoList.get(index);
        stopDownload(downloadInfo);
    }

    public synchronized void stopDownload(DownloadInfo downloadInfo) throws DbException {
        HttpHandler<File> handler = downloadInfo.getHandler();
        if (handler != null && !handler.isCancelled()) {
            handler.cancel();
        } else {
            downloadInfo.setState(HttpHandler.State.CANCELLED);
        }
        db.saveOrUpdate(downloadInfo);
    }

    public synchronized void stopAllDownload() throws DbException {
        for (DownloadInfo downloadInfo : downloadInfoList) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null && !handler.isCancelled()) {
                handler.cancel();
            } else {
                downloadInfo.setState(HttpHandler.State.CANCELLED);
            }
        }
        db.saveOrUpdateAll(downloadInfoList);
    }

    public synchronized void backupDownloadInfoList() throws DbException {
        for (DownloadInfo downloadInfo : downloadInfoList) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
        }
        db.saveOrUpdateAll(downloadInfoList);
    }

    public int getMaxDownloadThread() {
        return maxDownloadThread;
    }

    public void setMaxDownloadThread(int maxDownloadThread) {
        this.maxDownloadThread = maxDownloadThread;
    }

    private RemoteViews mRemoteViews;
    /** 显示自定义的带进度条通知栏 */
    private void showCustomProgressNotify(DownloadInfo downloadInfo) {
        if(mBuilder == null){
            return ;
        }

        if(mRemoteViews == null){
            mRemoteViews = new RemoteViews(mContext.getPackageName(),R.layout.notification_item);
        }
        mRemoteViews.setImageViewResource(R.id.iv_image, R.drawable.a1);
        mRemoteViews.setTextViewText(R.id.tv_name, downloadInfo.getFileName());
        int progress = 0;
        if(downloadInfo.getProgress() != 0){
            if(downloadInfo.getFileLength()!=0){
                progress = (int) (downloadInfo.getProgress() * 100 / downloadInfo.getFileLength());
            }
        }
        if(progress >= 100){
            mRemoteViews.setProgressBar(R.id.pb_progress, 100, 100, false);
            mRemoteViews.setTextViewText(R.id.tv_state, "下载完成，点击安装");
//            mRemoteViews.setViewVisibility(R.id.pb_progress, View.GONE);
            mBuilder.setContentIntent(getPendingIntent(true,downloadInfo));
            mBuilder.setAutoCancel(true);
        }else{
            mRemoteViews.setTextViewText(R.id.tv_state, "进度："+progress+"%");
            mRemoteViews.setProgressBar(R.id.pb_progress, 100, progress, false);
//            mRemoteViews.setViewVisibility(R.id.pb_progress, View.VISIBLE);
            mBuilder.setContentIntent(getPendingIntent(false,downloadInfo));
        }
        if(downloadInfo.getState() == HttpHandler.State.CANCELLED){
            mRemoteViews.setTextViewText(R.id.tv_state, "已暂停");
        }

        Notification notification = mBuilder.build();
        notification.contentView = mRemoteViews;
        mNotificationManager.notify((int) downloadInfo.getId(), notification);
    }

    public class ManagerCallBack extends RequestCallBack<File> {
        private DownloadInfo downloadInfo;
        private RequestCallBack<File> baseCallBack;

        public RequestCallBack<File> getBaseCallBack() {
            return baseCallBack;
        }

        public void setBaseCallBack(RequestCallBack<File> baseCallBack) {
            this.baseCallBack = baseCallBack;
        }

        private ManagerCallBack(DownloadInfo downloadInfo, RequestCallBack<File> baseCallBack) {
            this.baseCallBack = baseCallBack;
            this.downloadInfo = downloadInfo;
        }

        @Override
        public Object getUserTag() {
            if (baseCallBack == null) return null;
            return baseCallBack.getUserTag();
        }

        @Override
        public void setUserTag(Object userTag) {
            if (baseCallBack == null) return;
            baseCallBack.setUserTag(userTag);
        }

        @Override
        public void onStart() {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }

            //通知观察者数据改变 by lqm
            DataChanger.getInstance().notifyDownloadDataChange();
            //更新通知栏
            showCustomProgressNotify(downloadInfo);
            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            if (baseCallBack != null) {
                baseCallBack.onStart();
            }
        }

        @Override
        public void onCancelled() {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }

            //通知观察者数据改变 by lqm
            DataChanger.getInstance().notifyDownloadDataChange();
            //更新通知栏
            showCustomProgressNotify(downloadInfo);

            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            if (baseCallBack != null) {
                baseCallBack.onCancelled();
            }
        }

        @Override
        public synchronized void onLoading(long total, long current, boolean isUploading) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
            downloadInfo.setFileLength(total);
            downloadInfo.setProgress(current);
//            try {
//                db.saveOrUpdate(downloadInfo);
//            } catch (DbException e) {
//                LogUtils.e(e.getMessage(), e);
//            }
            //通知观察者数据改变 by lqm
            DataChanger.getInstance().notifyDownloadDataChange();
            //更新通知栏
            showCustomProgressNotify(downloadInfo);

            if (baseCallBack != null) {
                baseCallBack.onLoading(total, current, isUploading);
            }
        }

        @Override
        public void onSuccess(ResponseInfo<File> responseInfo) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }

            //通知观察者数据改变 by lqm
            DataChanger.getInstance().notifyDownloadDataChange();
            //更新通知栏
            showCustomProgressNotify(downloadInfo);

            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            if (baseCallBack != null) {
                baseCallBack.onSuccess(responseInfo);
            }
        }

        @Override
        public void onFailure(HttpException error, String msg) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }

            //通知观察者数据改变 by lqm
            DataChanger.getInstance().notifyDownloadDataChange();
            //更新通知栏
            showCustomProgressNotify(downloadInfo);

            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            if (baseCallBack != null) {
                baseCallBack.onFailure(error, msg);
            }
        }
    }

    public List<DownloadInfo> getDownloadInfoList() {
        return downloadInfoList;
    }

    private class HttpHandlerStateConverter implements ColumnConverter<HttpHandler.State> {

        @Override
        public HttpHandler.State getFieldValue(Cursor cursor, int index) {
            return HttpHandler.State.valueOf(cursor.getInt(index));
        }

        @Override
        public HttpHandler.State getFieldValue(String fieldStringValue) {
            if (fieldStringValue == null) return null;
            return HttpHandler.State.valueOf(fieldStringValue);
        }

        @Override
        public Object fieldValue2ColumnValue(HttpHandler.State fieldValue) {
            return fieldValue.value();
        }

        @Override
        public ColumnDbType getColumnDbType() {
            return ColumnDbType.INTEGER;
        }
    }
}
