package com.webimapp.android.demo.client;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ErrorFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_error, container, false);
        Bundle args = getArguments();
        ((TextView) v.findViewById(R.id.errorHeader)).setText(args.getInt("h"));
        ((TextView) v.findViewById(R.id.errorDesc)).setText(getResources()
                .getString(args.getInt("d"), (Object[]) args.getStringArray("a")));
        return v;
    }

    public static ErrorFragment newInstance(int errorHeaderId,
                                            int errorDescId,
                                            String... formatArgs) {
        Bundle args = new Bundle();
        args.putInt("h", errorHeaderId);
        args.putInt("d", errorDescId);
        args.putStringArray("a", formatArgs);
        ErrorFragment fragment = new ErrorFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
