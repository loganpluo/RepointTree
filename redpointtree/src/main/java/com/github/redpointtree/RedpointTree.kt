package com.github.redpointtree

import android.content.Context
import android.support.annotation.XmlRes
import android.text.TextUtils
import android.view.InflateException
import com.github.redpointtree.util.LogUtil
import com.tencent.mmkv.MMKV
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import android.support.annotation.StringRes
import android.util.AttributeSet
import android.util.Xml


/**
 * Created by loganpluo on 2019/4/14.
 */
class RedpointTree(ctx: Context, val name:String, @XmlRes val xml:Int, defaultLoadCache:Boolean = true) {
    val tag = "RedPointTree"
    private val context:Context = ctx.applicationContext
    private var rootRedPoint:RedPoint

    private val redPointClearIntentMap = HashMap<String,RedPoint>()
    private val redPointClearUrlMap = HashMap<String,RedPoint>()

    companion object{
        fun findRedPointByTag(tag:String, redPoint: RedPoint):List<RedPoint>{

            val findTagRedPointList = ArrayList<RedPoint>()
            findRedPointByTag(tag,redPoint,findTagRedPointList)

            return findTagRedPointList
        }

        private fun findRedPointByTag(tag:String, redPoint: RedPoint, list:MutableList<RedPoint>){

            if(redPoint.tag == tag){
                list.add(redPoint)
            }
            if(redPoint is RedPointGroup){
                redPoint.childrenList.forEach {
                    findRedPointByTag(tag, it, list)
                }
            }

        }
    }

    init {
        rootRedPoint = parseXml(context, xml, defaultLoadCache)
    }

    fun getRootRedPoint():RedPoint{
        return rootRedPoint
    }

    fun findRedPointById(@StringRes id:Int):RedPoint?{
        return findRedPointById(context.getString(id))
    }

    fun findRedPointById(id:String):RedPoint?{
        if(id == rootRedPoint.getId()){
            return rootRedPoint
        }
        if(rootRedPoint is RedPointGroup){
            return (rootRedPoint as RedPointGroup).findRedPointById(id)
        }

        if(id == rootRedPoint.getId()){
            return rootRedPoint
        }

        return null
    }

    fun invalidate(){
        rootRedPoint?.invalidate()
    }

    private fun parseXml(context: Context, xml:Int, defaultLoadCache:Boolean):RedPoint{
        val parser = context.resources.getXml(xml)
//        var eventType = parser.eventType
//        LogUtil.d(tag,"parseXml start eventType:$eventType")

        var root:RedPoint?
        try{
            val attrs = Xml.asAttributeSet(parser)
            // Look for the root node.
            var type: Int = parser.next()
            while ((type) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                type = parser.next()
//                LogUtil.d(tag,"parseXml type:$type, parser.name:${parser.name}")
            }

            if (type != XmlPullParser.START_TAG) {
                throw InflateException(parser.positionDescription + ": No start tag found!")
            }

            if("RedPoint" == parser.name){
                root = createRedPoint(attrs,defaultLoadCache)
            }else if("RedPointGroup" == parser.name){
                root = createRedPointGroup(attrs)
                rInflateChildren(parser, root, attrs, defaultLoadCache)
            }else{
                throw UnSupportException("unkown tag ${parser.name}")
            }

        } catch (e: XmlPullParserException) {
            throw e
        } catch (e: Exception ) {
            throw e
        } finally {
            parser.close()
        }


        return root!!
    }

    private fun createRedPointGroup(attributeSet: AttributeSet):RedPointGroup{
        var redPointGroup:RedPointGroup? = null

        val theme = context.theme
        val typedArray = theme.obtainStyledAttributes(attributeSet, R.styleable.RedPoint, 0, 0)
        val count = typedArray.indexCount

        (0 until count)
                .map { typedArray.getIndex(it) }
                .forEach {
                    when(it){
                        R.styleable.RedPoint_id -> {
                            val id = typedArray.getString(it)
                            if(!TextUtils.isEmpty(id)){
                                redPointGroup = RedPointGroup(id)
                                LogUtil.d(tag,"createRedPointGroup id:$id")
                            }

                        }
                    }
                }

        if(redPointGroup != null) return redPointGroup!!

        throw CreateRedPointGroupException("createRedPointGroup failed, id is empty")
    }

    class CreateRedPointGroupException(msg:String):Exception(msg)

    private fun createRedPoint(attributeSet: AttributeSet,defaultLoadCache:Boolean):RedPoint?{
        var redPoint:RedPoint? = null

        val theme = context.theme
        val typedArray = theme.obtainStyledAttributes(attributeSet, R.styleable.RedPoint, 0, 0)
        val count = typedArray.indexCount

        for(i in 0 until count){
            val styledAttr = typedArray.getIndex(i)
            when(styledAttr){
                R.styleable.RedPoint_id -> {
                    val id = typedArray.getString(styledAttr)
                    if(!TextUtils.isEmpty(id)){
                        if(redPoint == null){
                            redPoint = RedPoint(id)
                        }else{
                            redPoint.setId(id)
                        }
                        LogUtil.d(tag,"createRedPoint init id:$id")
                    }
                }
                R.styleable.RedPoint_needCache ->{
                    val needCache = typedArray.getBoolean(styledAttr,false)

                    if(redPoint == null){
                        redPoint = RedPoint("")
                    }

                    if(needCache){

                        var cacheUnReadCount = -1//默认必须是-1，这样RedPointWriteCacheObserver中 在defaultLoadCache为false的时候，可以写入0
                        if(defaultLoadCache){
                            val cacheKey = redPoint.getCacheKey()
                            cacheUnReadCount = MMKV.defaultMMKV().getInt(cacheKey,0)

                            redPoint.setUnReadCount(cacheUnReadCount)
                            LogUtil.d(tag,"createRedPoint id:${redPoint.getId()}, cacheKey:$cacheKey, cacheUnReadCount:$cacheUnReadCount")
                        }

                        redPoint.addObserver(object:RedPointWriteCacheObserver{
                            //case1:defaultLoadCache为true，preUnReadCount能防止首次刷新写重复的缓存值;
                            //case2:defaultLoadCache为false，默认值必须是-1，这样设置0能成功
                            var preUnReadCount = cacheUnReadCount
                            override fun notify(unReadCount: Int) {

                                val newCacheKey = redPoint!!.getCacheKey()
                                if(preUnReadCount != unReadCount && !TextUtils.isEmpty(newCacheKey)){
                                    LogUtil.i(tag,"createRedPoint RedPointWriteCacheObserver id:${redPoint!!.getId()}, notify success cacheKey:$newCacheKey, unReadCount:$unReadCount")
                                    MMKV.defaultMMKV().putInt(newCacheKey,unReadCount)
                                }else{
                                    LogUtil.i(tag,"createRedPoint RedPointWriteCacheObserver id:${redPoint!!.getId()}, notify no need cacheKey:$newCacheKey, unReadCount:$unReadCount, preUnReadCount:$preUnReadCount")
                                }
                            }
                        })

                    }

                }

                R.styleable.RedPoint_clearIntent->{
                    val clearIntent = typedArray.getString(styledAttr)
                    if(!TextUtils.isEmpty(clearIntent)){
                        if(redPoint == null){
                            redPoint = RedPoint("")
                        }
                        clearIntent.split(",").forEach {
                            redPointClearIntentMap.put(it, redPoint!!)
                        }
                    }
                }
                R.styleable.RedPoint_clearUrl->{
                    val clearUrl = typedArray.getString(styledAttr)
                    if(!TextUtils.isEmpty(clearUrl)){
                        if(redPoint == null){
                            redPoint = RedPoint("")
                        }
                        clearUrl.split(",").forEach {
                            redPointClearUrlMap.put(it,redPoint)
                        }

                    }
                }

            }
        }

        //todo throw
        return redPoint
    }



    private fun rInflateChildren(parser:XmlPullParser,
                                 parent:RedPointGroup,
                                 attributeSet: AttributeSet,
                                 defaultLoadCache:Boolean){

        val depth = parser.depth
        var type: Int = parser.next()

        while ((type != XmlPullParser.END_TAG || parser.depth > depth) && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                type = parser.next()
                continue
            }

            val name = parser.name
//            LogUtil.d(tag,"rInflateChildren name:$name, type:$type")
            //继续递归添加
            if("RedPointGroup" == name){
                val currentRedPoint = createRedPointGroup(attributeSet)
                parent.addChild(currentRedPoint)
                rInflateChildren(parser, currentRedPoint, attributeSet,defaultLoadCache)
            }else if("RedPoint" == name){
                val currentRedPoint = createRedPoint(attributeSet, defaultLoadCache)
                if(currentRedPoint != null){
                    parent.addChild(currentRedPoint)
                }
            }

            type = parser.next()
        }

    }

    fun loadCache(){
        LogUtil.i(tag,"loadCache")
        loadCache(rootRedPoint)
        rootRedPoint.invalidate(false)
    }

    private fun loadCache(redPoint:RedPoint){
        if(redPoint is RedPointGroup){
            redPoint.getChildren().forEach {
                loadCache(it)
            }
            return
        }
        val cacheUnReadCount = MMKV.defaultMMKV().getInt(redPoint.getCacheKey(),0)
        if(cacheUnReadCount != 0){
            LogUtil.i(tag,"id:${redPoint.getId()}, loadCache cacheUnReadCount:$cacheUnReadCount")
            redPoint.setUnReadCount(cacheUnReadCount)
        }

    }

    fun clearUnReadCount(needWriteCache:Boolean){
        LogUtil.i(tag,"clearUnReadCount needWriteCache:$needWriteCache")
        setUnReadCount(rootRedPoint, 0)

        rootRedPoint.invalidate(needWriteCache)
    }

    fun setUnReadCount(redPoint:RedPoint, unReadCount:Int){
        if(redPoint is RedPointGroup){
            redPoint.getChildren().forEach {
                setUnReadCount(it,unReadCount)
            }
            return
        }

        redPoint.setUnReadCount(unReadCount)

    }


    fun print(tag:String){
        val stringBuilder = StringBuilder()
        append(0, rootRedPoint, stringBuilder)
        LogUtil.d(tag+"|"+this.tag,"redpointTreeName:$name \n $stringBuilder")
    }

    private fun append(treeLevel:Int, redPoint:RedPoint,stringBuilder:StringBuilder){
        val preBlank = StringBuilder()
        LogUtil.d(tag,"append treeLevel:$treeLevel")
        for(i in 0 until treeLevel){
            preBlank.append("   ")
        }

        stringBuilder.append(preBlank)

        if(redPoint is RedPointGroup){
            stringBuilder.append("<RedPointGroup id=${redPoint.getId()} unReadCount=${redPoint.getUnReadCount()}/>\n")

            redPoint.getChildren().forEach {
                append(treeLevel+1, it, stringBuilder)
            }
            return
        }

        stringBuilder.append("<RedPoint id=${redPoint.getId()} unReadCount=${redPoint.getUnReadCount()}/>\n")

    }

    fun clearByIntent(clearIntent:String){
        val redPoint = redPointClearIntentMap[clearIntent]
        if(redPoint != null){
            LogUtil.d(tag,"clearByIntent $clearIntent, $redPoint")
            redPoint.invalidate(0)
        }

    }

    fun clearByUrl(clearUrl:String){
        redPointClearUrlMap[clearUrl]?.invalidate(0)
    }

    class UnSupportException(val msg:String): Exception(msg)

}