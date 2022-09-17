package de.nidoca.webview.iptv;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.gms.cast.framework.CastContext;

import de.nidoca.webview.iptv.databinding.FragmentPlayerBinding;

public class FirstFragment extends Fragment {

    private FragmentPlayerBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentPlayerBinding.inflate(inflater, container, false);

        ExoPlayer player = new ExoPlayer.Builder(inflater.getContext()).build();
        binding.playerView.setPlayer(player);
        MediaItem mediaItem = MediaItem.fromUri("http://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8");
        player.setMediaItem(mediaItem);


        binding.playerView.setPlayer(player);


        player.prepare();
        player.play();


        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}