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
    // Create some Person entities
    val person1 = Person()
    val person2 = Person()
    
    // Create some Car entities
    val car1 = Car()
    val car2 = Car()
    val car3 = Car()
    
    car1.CarPlate = "1234BNZ"
    car1.Brand = "Mazda"
    car1.Model = "2"
    
    car2.CarPlate = "5432DSJ"
    car2.Brand = "Toyota"
    car2.Model = "Avensis"
    
    car3.CarPlate = "4356KDD"
    car3.Brand = "Mazda"
    car3.Model = "CX-5"
    
    person1.FirstName = "Jane"
    person1.LastName = "Nobody"
    person1.Cars.addAll(arrayListOf(car1, car2))
    
    person2.FirstName = "John"
    person2.LastName = "Nobody"
    person2.Cars.add(car3)
    
    // If we don't persist car entities, when we save persons, recursivelly save the cars too
    if (person1.save() && person2.save()){
      // because we have persons, we can filter using lambda or primary key loads...
      val personPk = Person()
      personPk.Id = 1 // We suppose person 1 has id = 1
      if (personPk.read()){
        // Successfully readed person...
              
        // we can delete entities...
        if (personPk.delete()){
          // if we have deleted the entity...
        }
      }
      
      // we can filter first level entities (don't filter sub entities, like arrays...)
      val fPersons = Person::class.filter { x -> x.LastName == "Nobody"} // returns a soft loaded entities (subentities, only with ids)
      val fullPersons = Person::class.fullFilter { x -> x.LastName == "Nobody"} // returns a full loaded entities      
    }
  ```

**If you think I've done a good job, you can consider contributing to the development of this library. [PaypalMe!](https://www.paypal.me/TaRSyS)**
