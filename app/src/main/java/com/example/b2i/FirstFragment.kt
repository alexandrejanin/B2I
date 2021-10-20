package com.example.b2i

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log.d
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import com.example.b2i.databinding.FragmentFirstBinding
import com.google.android.gms.location.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationRequest: LocationRequest = LocationRequest.create()
    private lateinit var locationCallback: LocationCallback

    private var _bluetoothManager: BluetoothManager? = null

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val uuid: ParcelUuid = ParcelUuid.fromString("00002aae-0000-1000-8000-00805f9b34fb")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        locationRequest.interval = 500
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                locationResult.lastLocation ?: return
                onLocationUpdate(locationResult)
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _bluetoothManager = requireContext().getSystemService()
        d("_bluetoothManager", _bluetoothManager.toString())
        val scanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                if (result.scanRecord?.serviceData?.containsKey(uuid) == true)
                    _binding?.scanText?.text =
                        result.scanRecord?.serviceData?.get(uuid)?.let { String(it) }
            }
        }

        val scanFilter = mutableListOf(
            ScanFilter.Builder()
                .build()
        )

        val scanSettings = ScanSettings.Builder().build()

        _bluetoothManager?.adapter?.bluetoothLeScanner?.startScan(
            scanFilter,
            scanSettings,
            scanCallback
        )

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    private fun onLocationUpdate(locationResult: LocationResult) {
        updateDisplay(locationResult)
        broadcastData(locationResult)
    }

    private fun updateDisplay(locationResult: LocationResult) {
        _binding?.coordinatesText?.text =
            "Last ${locationResult.locations.size} locations recorded" +
                    "\nLongitude: ${locationResult.lastLocation.longitude}" +
                    "\nLatitude: ${locationResult.lastLocation.latitude}" +
                    "\nBearing: ${locationResult.lastLocation.bearing}" +
                    "\nSpeed: ${locationResult.lastLocation.speed}"
    }

    private fun broadcastData(locationResult: LocationResult) {
        _bluetoothManager ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(1000)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val payload: ByteArray =
            "${locationResult.lastLocation.latitude},${locationResult.lastLocation.longitude}".toByteArray()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(uuid, payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                d("onStartFailure", "onStartFailure")
            }
        }

        _bluetoothManager?.adapter?.bluetoothLeAdvertiser?.startAdvertising(
            settings,
            data,
            callback
        )

    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            ||
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.permissionsButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}