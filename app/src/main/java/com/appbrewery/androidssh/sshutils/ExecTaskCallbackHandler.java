package com.appbrewery.androidssh.sshutils;


public interface ExecTaskCallbackHandler {

    void onFail();

    void onComplete(String completeString);
}
