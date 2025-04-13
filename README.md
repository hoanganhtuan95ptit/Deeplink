# MultiTypeAdapter - Android Library 🚀

**MultiTypeAdapter** is an Android library that simplifies handling **RecyclerView** with multiple item types, reduces boilerplate code, and optimizes performance when working with complex lists. 🎉

## Features ✨

- **Automatically differentiate item types** in RecyclerView without writing complex `getItemViewType()` logic 🔄.
- **Support for multiple item types** in a single RecyclerView 📑.
- **Optimized RecyclerView updates**: Only update changed items, improving performance ⚡.
- **Easy to use**: No need to rewrite code for each item type 👨‍💻.
- **Supports DiffUtil**: Automatically compares and updates changes in the list 🔍.

## Installation ⚙️

Add the dependency to your project's `build.gradle` file:

### 1. Add to your project's `build.gradle`
```gradle
dependencies {
    implementation 'com.example:multitypeadapter:1.0.0'
}
```
### 2. Add to your module's `build.gradle`
```gradle
dependencies {
    implementation 'com.example:multitypeadapter:1.0.0' 
    annotationProcessor 'com.example:multitypeadapter-processor:1.0.0'
}
```

## Usage Guide 📚

### 1. Create an Item Adapter
``` kotlin
@ItemAdapter
class TestAdapter : ViewItemAdapter<TestViewItem, ItemTestBinding>() {

    override fun onBindViewHolder(binding: ItemTestBinding, viewType: Int, position: Int, item: TestViewItem, payloads: MutableList<Any>) {
        super.onBindViewHolder(binding, viewType, position, item, payloads)

        if (payloads.contains(PAYLOAD_TEXT)) refreshText(binding, item)
    }

    override fun onBindViewHolder(binding: ItemTestBinding, viewType: Int, position: Int, item: TestViewItem) {
        super.onBindViewHolder(binding, viewType, position, item)

        refreshText(binding, item)
    }

    private fun refreshText(binding: ItemTestBinding, item: TestViewItem) {
        binding.tvText.text = item.text
    }
}

data class TestViewItem(
    val id: String,
    val text: String
) : ViewItem {

    override fun areItemsTheSame(): List<Any> = listOf(
        id
    )

    override fun getContentsCompare(): List<Pair<Any, String>> = listOf(
        text to PAYLOAD_TEXT
    )
}

private const val PAYLOAD_TEXT = "TEXT"
```
### 2. Basic Usage
```kotlin

val list = arrayListOf<ViewItem>()

for (i in 0..10) {
    if (i % 2 == 0) list.add(TestViewItem(id = "$i", text = "index: $i"))
    else list.add(Test2ViewItem(id = "$i", text = "index: $i"))
}

binding.recyclerView.adapter = MultiAdapter()
binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
binding.recyclerView.submitListAwait(list)
```
### 3. Advanced Usage

Support for adapters that accept click events through the constructor.
```kotlin
val list = arrayListOf<ViewItem>()

for (i in 0..10) {
    if (i % 2 == 0) list.add(TestViewItem(id = "$i", text = "index: $i"))
    else list.add(Test2ViewItem(id = "$i", text = "index: $i"))
}

// Khởi tạo adapter với sự kiện click và long click
val advancedAdapter = AdvancedAdapter(
    onItemClick = { testViewItem ->
        Toast.makeText(this, "Item clicked: ${testViewItem.text}", Toast.LENGTH_SHORT).show() 
    },
    onItemLongClick = { testViewItem ->
        Toast.makeText(this, "Item long clicked: ${testViewItem.text}", Toast.LENGTH_SHORT).show()
    }
)

binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
binding.recyclerView.adapter = MultiAdapter(advancedAdapter)
binding.recyclerView.submitListAwait(list)
```