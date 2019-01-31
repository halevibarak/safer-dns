/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package comm.dns.main;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import comm.dns.Configuration;
import comm.dns.FileHelper;
import comm.dns.ItemChangedListener;
import comm.dns.MainActivity;
import comm.dns.R;
import comm.dns.db.RuleDatabaseUpdateJobService;

public class HostsFragment extends Fragment implements FloatingActionButtonFragment {

    private ItemRecyclerViewAdapter mAdapter;

    public HostsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_hosts, container, false);

        RecyclerView mRecyclerView = (RecyclerView) rootView.findViewById(R.id.host_entries);

        mRecyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(mLayoutManager);


        mAdapter = new ItemRecyclerViewAdapter(MainActivity.config.hosts.items, 3);
        mRecyclerView.setAdapter(mAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelperCallback(mAdapter));
        itemTouchHelper.attachToRecyclerView(mRecyclerView);

        Switch hostEnabled = (Switch) rootView.findViewById(R.id.host_enabled);
        hostEnabled.setChecked(MainActivity.config.hosts.enabled);
        hostEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.hosts.enabled = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        Switch automaticRefresh = (Switch) rootView.findViewById(R.id.automatic_refresh);
        automaticRefresh.setChecked(MainActivity.config.hosts.automaticRefresh);
        automaticRefresh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.hosts.automaticRefresh = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
                RuleDatabaseUpdateJobService.scheduleOrCancel(getContext(), MainActivity.config);
            }
        });


        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "hosts");

        return rootView;
    }

    public void setupFloatingActionButton(FloatingActionButton fab) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final MainActivity main = (MainActivity) getActivity();
                main.editItem(3, null, new ItemChangedListener() {
                    @Override
                    public void onItemChanged(Configuration.Item item) {
                        MainActivity.config.hosts.items.add(item);
                        mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
                        FileHelper.writeSettings(getContext(), MainActivity.config);
                    }
                });
            }
        });
    }

}
