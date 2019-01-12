## TaRSySAndroidORMKotlin (Yet Another Android ORM)

### Why I created this ORM if there are already many others?

I created this ORM for one simple reason ... I could not find one that would cover all my needs ...

I was looking for a ORM that was easy to deploy, powerful, and without any interaction by the developer at the time of making changes to the persistent entities. Previously, I developed an ORM for Java ([TaRSySAndroidORM](http://tarsys.github.io/TaRSySAndroidORM/)), but I saw that there was not one developed with Kotlin and for Kotlin to cover my needs...

### How it works?

The integration with your project is very simple, let's see it in somesteps:

1. Add a reference in your build.gradle.
  ```markdown
  implementation 'com.github.tarsys.android:kotlin-orm:1.0.2.8'
  ```
2. Add some meta tags to your Manifest.xml
  ```xml
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme" >

        <meta-data android:name="IS_EXTERNALSTORAGE" android:value="true" />
        <meta-data android:name="DATABASE_DIRECTORY" android:value="ExampleTaRSySORM" />
        <meta-data android:name="DATABASE_NAME" android:value="exampletarsysorm.db" />
        <meta-data android:name="ENTITY_PACKAGES" android:value="com.tarsys.examplekotlinorm.entities" />
        <meta-data android:name="DB_STRING_DEFAULT_LENGTH" android:value="500" />

        <activity android:name=".MainActivity" >
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
  ```
3. Add in your Launcher activity this code:
  ```kotlin
    if (SGBDEngine.initialize(this)){
      // TODO: Do some stuff
    }else{
      // if something wrong, do other stuff
    }
  ```
4. Create the entity classes (**All classes must implement the IOrmEntity Interface**):
  ```kotlin
    package com.tarsys.examplekotlinorm.entities
  
    @DBEntity
    class Car: Serializable, IOrmEntity {
      @TableField(PrimaryKey = true)
      var Id: Int = 0
      @TableField
      var CarPlate: String = AndroidSupport.EmptyString
      @TableField
      var Brand: String = AndroidSupport.EmptyString
      @TableField
      var Model: String = AndroidSupport.EmptyString
    }
  
    @DBEntity
    class Person: Serializable, IOrmEntity {
      @TableField(PrimaryKey = true)
      var Id: Int = 0
      @TableField(DataTypeLength = 60)
      var FirstName: String = AndroidSupport.EmptyString
      @TableField(DataTypeLength = 60)
      var LastName: String = AndroidSupport.EmptyString
      @TableField(DataType = DBDataType.EntityListDataType, EntityClass = Car::class, CascadeDelete = true)
      var Cars: ArrayList<Car> = arrayListOf()
    }
  ```
5. Use it!
  ```kotlin
  ```


