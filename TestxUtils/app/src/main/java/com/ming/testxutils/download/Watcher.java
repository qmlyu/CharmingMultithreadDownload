package com.ming.testxutils.download;

import java.util.Observable;
import java.util.Observer;

/**
 * Created by CharmingLee on 2015/5/28.
 */
public abstract class Watcher implements Observer {
    @Override
      public void update(Observable observable, Object data) {
        //通知子类观察者数据改变
        ontifyDownloadDataChange();
    }

    public abstract void ontifyDownloadDataChange();
}
