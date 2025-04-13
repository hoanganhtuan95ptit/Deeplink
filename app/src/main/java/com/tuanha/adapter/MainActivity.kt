package com.tuanha.adapter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tuanha.app.databinding.ActivityMainBinding
import com.tuanha.deeplink.C
import java.util.ServiceLoader

//import com.tuanha.deeplink.sendDeeplink

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val plugins = ServiceLoader.load(C::class.java)
        plugins.forEach {
            it.run()
        }
//        sendDeeplink("app://home")
//        sendDeeplink("app://home")

//        lifecycleScope.launch {
//
//            val list = arrayListOf<ViewItem>()
//
//            for (i in 0..10) {
//                if (i % 2 == 0) list.add(TestViewItem(id = "$i", text = "index: $i"))
//                else list.add(Test2ViewItem(id = "$i", text = "index: $i"))
//            }
//
//            binding.recyclerView.adapter = MultiAdapter()
//            binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
//            binding.recyclerView.submitListAwait(list)
//        }
    }
}