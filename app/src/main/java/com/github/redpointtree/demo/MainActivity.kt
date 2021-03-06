package com.github.redpointtree.demo

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.github.redpointtree.RedPointTextView
import com.github.redpointtree.RedPointTreeCenter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    val tag = "MainActivity|RedpointTree"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        RedPointTreeCenter.getInstance().put(this, R.string.messagebox_tree, R.xml.messagebox)
//        RedPointTreeCenter.getInstance().getRedPointTree(resources.getString(R.string.messagebox_tree))
//                ?.findRedPointById(R.string.messagebox_system)?.setIsMuteToParent(true)
//        RedPointTreeCenter.getInstance().getRedPointTree(resources.getString(R.string.messagebox_tree))
//                ?.findRedPointById(R.string.messagebox_moment)?.isMuteToParent = true
        gotoRedPointTreeInSimpleActivity.setOnClickListener {
            val intent = Intent(this@MainActivity,RedPointTreeInSimpleActivity::class.java)
            startActivity(intent)
        }

        gotoCrossHierarchyActivity.setOnClickListener {
            val intent = Intent(this@MainActivity,CrossHierarchyActivity::class.java)
            startActivity(intent)
        }

        RedPointTextView(this).treeName = ""

    }


}
