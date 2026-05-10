package com.sirelon.sellsnap.features.seller.categories

import com.sirelon.sellsnap.di.applicationScopeQualifier
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeValidator
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import com.sirelon.sellsnap.features.seller.categories.presentation.CategoryPickerViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val categoriesModule = module {
    factoryOf(::AttributeValidator)
    factoryOf(::CategoriesMapper)
    single { CategoriesRepository(get(), get(), get(applicationScopeQualifier)) }
    viewModelOf(::CategoryPickerViewModel)
}
