package com.example.b2i

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.ParcelUuid
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

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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
        context ?: return
        val bluetoothManager = requireContext().getSystemService<BluetoothManager>()
            ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(1000)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val payload: ByteArray = "test".toByteArray()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(
                ParcelUuid.fromString("5dd9bb0f-0d4a-4f03-aeec-070a700b8ff3"),
                payload,
            )
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {}
        }

        bluetoothManager.adapter.bluetoothLeAdvertiser.startAdvertising(settings, data, callback);
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