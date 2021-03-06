package com.example.administrator.audiodecoder;

import android.media.MediaFormat;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.autofill.AutofillId;

import com.net.sourceforge.resample.Resample;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class AudioMerge extends Thread implements AudioRawDataCallback{
    private static final String TAG = "AudioMerge";
    private ArrayList<String> mBackgroundMusicFile; //背景音mp3格式
    private ArrayList<String> mActorVoice; //配音wav格式
    private ArrayList<AudioNode> mAudioNodes;
    private Boolean mRunning = false;
    private List<AudioDecoding> mDecoderLst;
    private ReentrantLock lock = new ReentrantLock();
    private AudioDecoding.AudioFormat mFisrtAudioFormat = null;
    private AudioResample mBgmResample;
    private AudioResample mVoiceResample;
    // test
    FileOutputStream fileOutputStream = null;
    FileOutputStream voiceFileOutputStream = null;

    public AudioMerge() {
    }

    public void startMerge() {
        mRunning = true;
        this.start();
        mAudioNodes = new ArrayList<>();
        try {
            fileOutputStream = new FileOutputStream("/sdcard/bgm.pcm");
            voiceFileOutputStream = new FileOutputStream("/sdcard/voice.pcm");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void stopMerge() {
        try {
            fileOutputStream.flush();
            fileOutputStream.close();
            voiceFileOutputStream.flush();
            voiceFileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void  putWillMergedAudio(int type, String fileName) {
        AudioNode node = new AudioNode(type, fileName);
        lock.lock();
        mAudioNodes.add(node);
        lock.unlock();
    }

    @Override
    public void run() {
        Boolean bFirstAudio = true;
        while (mRunning) {
            if (mAudioNodes.size() != 0) {
                AudioNode node = mAudioNodes.get(0);
                mAudioNodes.remove(0);
                AudioDecoding decoder = new AudioDecoding(node.mFilePath, node.mFileType, this);

                if (!decoder.parse()) {
                    Log.e(TAG, "parse audio failed");
                    return;
                }
                // 保存第一个音频的格式，后续的音频都转成这个格式
                if (bFirstAudio) {
                    mFisrtAudioFormat = decoder.getMediaFormat();
                }

                if (!mFisrtAudioFormat.equals(decoder.getMediaFormat())) {
                    decoder.setParams(true);
                }

                // decode
                decoder.start();
                bFirstAudio = false;

            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void BgmDataCallBack(byte[] data, boolean needResample, AudioDecoding.AudioFormat format) {
        if (mFisrtAudioFormat == null) {
            Log.e(TAG, "first audio format is null");
            return;
        }

        if (needResample) {
            double factor = mFisrtAudioFormat.sampleRate/format.sampleRate;
            short[] sData = BytesTransUtils.toShortArray(data);
            short[] resampledData = new short[sData.length];
            Resample.resample(factor, sData, resampledData, sData.length);
            byte[] targetData = BytesTransUtils.toByteArray(resampledData);


        }

        // restore pcm
        try {
            fileOutputStream.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void VoiceDataCallBack(byte[] data, boolean needResample, AudioDecoding.AudioFormat format) {
        if (mFisrtAudioFormat == null) {
            Log.e(TAG, "first audio format is null");
            return;
        }

        if (needResample) {
            double factor = (double)mFisrtAudioFormat.sampleRate/format.sampleRate;
            short[] sData = BytesTransUtils.toShortArray(data);
            double len = sData.length;
            int resampledDataLen = (int) (len * factor);
            if (resampledDataLen % 2 == 0) {

            }
            short[] resampledData = new short[resampledDataLen];
            Resample.resample(factor, sData, resampledData, sData.length);
            byte[] targetData = BytesTransUtils.toByteArray(resampledData);
            try {
                voiceFileOutputStream.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void DataCallbackForResample(byte[] data) {

    }

    @Override
    public void DataCallBackForMixer(byte[] data) {

    }
}