package com.example.b2i

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log.d
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import com.example.b2i.databinding.FragmentFirstBinding
import com.google.android.gms.location.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private var bluetoothManager: BluetoothManager? = null

    private val serviceUuid: ParcelUuid =
        ParcelUuid.fromString("00002aae-0000-1000-8000-00805f9b34fb")

    private var scanMode = false
    private val broadcastMode get() = !scanMode

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            if (result.scanRecord?.serviceData?.containsKey(serviceUuid) == true)
                _binding?.scanText?.text =
                    result.scanRecord?.serviceData?.get(serviceUuid)?.let { String(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _binding?.scanText?.let { it.text = "Scan failed: Error ${errorCode}" }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.permissionsButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        }

        binding.modeCheckbox.setOnClickListener {
            if (it is CheckBox) {
                scanMode = it.isChecked
            }
        }

        bluetoothManager = requireContext().getSystemService()

        requestLocationUpdates()
        onChangeMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onChangeMode() {
        if (scanMode) {
            startScan()
        } else {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    private fun startScan() {
        val scanFilter = mutableListOf(
            ScanFilter.Builder()
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothManager?.adapter?.bluetoothLeScanner?.startScan(
            scanFilter,
            scanSettings,
            scanCallback
        )
    }

    private fun onLocationUpdate(location: Location) {
        updateLocationDisplay(location)
        if (broadcastMode)
            startLocationBroadcast(location)
    }

    private fun updateLocationDisplay(location: Location) {
        _binding?.coordinatesText?.text =
            "\nLongitude: ${location.longitude}" +
                    "\nLatitude: ${location.latitude}" +
                    "\nBearing: ${location.bearing}" +
                    "\nSpeed: ${location.speed}"
    }

    private fun startLocationBroadcast(location: Location) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val payload: ByteArray = "${location.latitude},${location.longitude}".toByteArray()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(serviceUuid, payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                d("onStartFailure", "onStartFailure(${errorCode})")
            }
        }

        bluetoothManager?.adapter?.bluetoothLeAdvertiser?.startAdvertising(
            settings,
            data,
            callback
        )
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            binding.permissionsButton.visibility = View.GONE
            val locationRequest: LocationRequest = LocationRequest.create()
                .setInterval(500)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

            val locationCallback: LocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation ?: return
                    onLocationUpdate(result.lastLocation)
                }
            }

            LocationServices.getFusedLocationProviderClient(requireContext())
                .requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
        }
    }
}