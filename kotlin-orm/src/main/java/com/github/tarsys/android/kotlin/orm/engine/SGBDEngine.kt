@file:Suppress("DEPRECATION")

package com.github.tarsys.android.kotlin.orm.engine

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import com.github.tarsys.android.support.utilities.AndroidSupport
import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.KClass

@Suppress("DEPRECATION")
class SGBDEngine {
    companion object {

        //region properties (public and private)

        var SQLiteDatabasePath: String = AndroidSupport.EmptyString
        var applicationInfo: ApplicationInfo? = null
        private val dbEntities: ArrayList<KClass<*>> = arrayListOf()
        private var entityContainers: String = AndroidSupport.EmptyString
        private var databaseName: String = AndroidSupport.EmptyString
        private var databaseFolder: String = AndroidSupport.EmptyString
        private var entityContainerPackages: ArrayList<String> = arrayListOf()
        private var isExternalStorage: Boolean = false

        //endregion

        fun entityClasses(context: Context, containers: String): ArrayList<KClass<*>>{
            val returnValue: ArrayList<KClass<*>> = arrayListOf()

            if (SGBDEngine.dbEntities.isEmpty()){
                try{
                    val packages: ArrayList<String> = arrayListOf()
                    val entities = HashMap<String, KClass<*>>()
                    val apkNameTmp = context.packageCodePath
                    val fileApk = File(apkNameTmp)
                    val apksPath = fileApk.parent
                    val pathApks = File(apksPath)

                    pathApks.listFiles { x -> x.absolutePath.endsWith(".apk") }
                            .forEach { apkFile ->
                                val classLoader = PathClassLoader(apkFile.absolutePath, Thread.currentThread().contextClassLoader)

                                try{
                                    packages.addAll(Arrays.asList(*containers.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
                                    val dexFile = DexFile(apkFile)
                                    dexFile.entries().toList().filter { x -> packages.firstOrNull { p -> x.startsWith(p) } != null }
                                        .forEach { classType ->
                                            try {
                                                val classTable = classLoader.loadClass(classType)?.kotlin

                                                if (classTable?.dbEntity != null) {
                                                    if (!(classTable.simpleName?.isEmpty() ?: true) && !entities.containsKey(classTable.simpleName!!))
                                                        entities[classTable.simpleName!!] = classTable
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                }catch (ex: java.lang.Exception){
                                    Log.e("TarSySORM-Kotlin", ex.message)
                                    ex.printStackTrace()
                                }
                            }

                    returnValue.addAll(entities.values.distinct())
                }catch (ex: java.lang.Exception){
                    Log.e("SGBD::entityClasses", ex.message)
                    ex.printStackTrace()
                    returnValue.clear()
                }
            }

            return returnValue
        }

        fun SQLiteDataBase(readOnly: Boolean): SQLiteDatabase?{
            var returnValue: SQLiteDatabase? = null

            if (SGBDEngine.SQLiteDatabasePath != null && !SGBDEngine.SQLiteDatabasePath.isEmpty()) {
                val openMode = if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE

                try {
                    returnValue = SQLiteDatabase.openDatabase(SQLiteDatabasePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or openMode)
                } catch (ex: Exception) {
                    Log.e(SGBDEngine::class.java.toString(), "Exception opening database " + SGBDEngine.SQLiteDatabasePath + ":\n" + ex.toString())
                    returnValue = null
                }

            } else {
                Log.e(SGBDEngine::class.java.toString(), "Database Path not stablished")
            }

            return returnValue
        }

        //region Engine initializers

        /**
         * Initialize SGBDEngine with Manifest metadata info
         */
        fun initialize(context: Context): Boolean{

            SGBDEngine.applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA)
            val containers = SGBDEngine.applicationInfo!!.metaData.getString("ENTITY_PACKAGES", "").replace(" ", "")

            return SGBDEngine.initialize(context, containers)
        }

        fun initialize(context: Context, entityContainers: String): Boolean{
            var returnValue = false
            try{
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)

                if (SGBDEngine.applicationInfo == null)
                    SGBDEngine.applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA)

                SGBDEngine.isExternalStorage = SGBDEngine.applicationInfo!!.metaData.getBoolean("IS_EXTERNALSTORAGE", false)
                SGBDEngine.dbEntities.clear()
                SGBDEngine.entityContainers = entityContainers
                SGBDEngine.databaseName = SGBDEngine.applicationInfo!!.metaData.getString("DATABASE_NAME", "${context.getPackageName()}.db")
                SGBDEngine.databaseFolder = SGBDEngine.applicationInfo!!.metaData.getString("DATABASE_DIRECTORY", "")
                SGBDEngine.SQLiteDatabasePath = (if (isExternalStorage) Environment.getExternalStorageDirectory().absolutePath + File.separator + SGBDEngine.databaseFolder
                else Environment.getDataDirectory().absolutePath) + File.separator + databaseName
                val packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                var savedAppVersion: Int = if (!File(SGBDEngine.SQLiteDatabasePath).exists()) 0 else prefs.getInt("AppVersion", 0)
                val currentAppVersion: Int = packageInfo.versionCode

                if (!SGBDEngine.entityContainers.isEmpty()) {
                    entityContainerPackages.addAll(Arrays.asList(*SGBDEngine.entityContainers.split(",".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()))
                }

                if (SGBDEngine.isExternalStorage){
                    if (!File(Environment.getExternalStorageDirectory().absolutePath + File.separator + SGBDEngine.databaseFolder).exists()) {
                        if (!File(Environment.getExternalStorageDirectory().absolutePath + File.separator + SGBDEngine.databaseFolder).mkdirs()) {
                            throw IOException("${Environment.getExternalStorageDirectory().absolutePath}${File.separator}${SGBDEngine.databaseFolder} NOT CREATED!")
                        }
                    }
                }

                if (savedAppVersion != currentAppVersion){
                    val sqlCreation: ArrayList<String> = arrayListOf()

                    SGBDEngine.entityContainerPackages.forEach {  e ->  sqlCreation.addAll(SGBDEngine.createSqlQuerys(SGBDEngine.createDatabaseModel(context, e)))}
                    sqlCreation.forEach { sql -> SGBDEngine.SQLiteDataBase(false)!!.execSQL(sql) }

                    prefs.edit().putInt("AppVersion", currentAppVersion).commit()
                }

                returnValue = true
            }catch (ex:Exception){
                Log.e("SGBDEngine", ex.message)
                ex.printStackTrace()
            }

            return returnValue
        }

        //endregion

        //region Methods for Data Model Object creation

        private fun createDatabaseModel(context: Context, packageName: String): ArrayList<DBTable>{
            val returnValue: ArrayList<DBTable> = arrayListOf()
            val entities: HashMap<String, DBTable> = hashMapOf()

            try{
                SGBDEngine.entityClasses(context, packageName)
                    .forEach { classTable ->
                        try{
                            if (classTable.dbEntity != null){
                                SGBDEngine.dbEntities.add(classTable)
                                val tables: ArrayList<DBTable> = classTable.dbTableModel
                                for(table in tables){
                                    if (!entities.containsKey(table.Table!!.TableName))
                                        entities[table.Table!!.TableName] = table
                                }
                            }
                        }catch (ex: java.lang.Exception){
                            Log.e("CreateDbModel", ex.message)
                            ex.printStackTrace()
                        }
                    }

                returnValue += entities.values
            }catch (ex: Exception){
                Log.e("SGBD::createDBM", ex.message)
                ex.printStackTrace()
                returnValue.clear()
            }

            return returnValue
        }

        private fun createSqlQuerys(dataModel: ArrayList<DBTable>): ArrayList<String> {
            val returnValue: ArrayList<String> = arrayListOf()
            val sqliteDb: SQLiteDatabase? = SGBDEngine.SQLiteDataBase(true)

            if (sqliteDb != null){
                returnValue += dataModel.flatMap{ x -> x.sqlCreationQuerys(sqliteDb) }

                if (sqliteDb.isOpen) sqliteDb.close()
            }

            return returnValue
        }

        //endregion

        //region Useful methods

        fun contentValuesToFilter(contentValues: ContentValues): Filter?{
            var returnValue: Filter? = null

            try{
                if (contentValues.size() > 0){
                    returnValue = Filter()

                    for (e in contentValues.valueSet()){
                        returnValue.FilterString += if (returnValue.FilterString.trim().equals("")) "" else " and "
                        returnValue.FilterString += e.key + "=?"
                        returnValue.FilterData.add(e.value.toString())
                    }
                }
            }catch (ex: Exception){
                Log.e("contentValuesToFilter", ex.message)
                ex.printStackTrace()
                returnValue = null
            }

            return returnValue
        }


        //endregion
    }
}