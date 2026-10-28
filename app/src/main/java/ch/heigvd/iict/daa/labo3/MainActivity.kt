package ch.heigvd.iict.daa.labo3

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * DAA - Labo 3 : contrôleur du formulaire d'édition d'une Person (Student ou Worker).
 *
 * L'activité fait le lien entre la vue (activity_main.xml) et le modèle (Person.kt) :
 *  - au lancement, le formulaire est initialisé avec une personne d'exemple ;
 *  - le bouton "Annuler" vide entièrement le formulaire ;
 *  - le bouton "OK" crée un Student ou un Worker avec les données saisies et l'affiche dans les logs.
 *
 * @author François de Courville, Jonatan Perret
 */
class MainActivity : AppCompatActivity() {

    // Données de base
    private lateinit var editName: EditText
    private lateinit var editFirstName: EditText
    private lateinit var editBirthDate: EditText
    private lateinit var btnBirthDate: ImageButton
    private lateinit var spinnerNationality: Spinner
    private lateinit var radioGroupOccupation: RadioGroup
    private lateinit var radioWorker: RadioButton

    // Données spécifiques aux étudiants
    private lateinit var groupStudent: Group
    private lateinit var editSchool: EditText
    private lateinit var editGraduationYear: EditText

    // Données spécifiques aux employés
    private lateinit var groupWorker: Group
    private lateinit var editCompany: EditText
    private lateinit var spinnerSector: Spinner
    private lateinit var editExperience: EditText

    // Données complémentaires
    private lateinit var editEmail: EditText
    private lateinit var editRemark: EditText

    // La date de naissance n'est pas éditable au clavier, on garde donc la valeur choisie ici
    // (null tant qu'aucune date n'a été sélectionnée)
    private var birthDay: Calendar? = null

    // Le formateur est créé avec la locale courante à chaque création de l'activité, il suit donc
    // un éventuel changement de langue du téléphone (cf. question 4.2)
    private val dateFormatter: DateFormat = DateFormat.getDateInstance(DateFormat.LONG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // depuis android 15 (sdk 35), le mode edge2edge doit être activé
        enableEdgeToEdge()

        // on spécifie le layout à afficher
        setContentView(R.layout.activity_main)

        // comme edge2edge est activé, l'application doit garder un espace suffisant pour la barre système
        // (ainsi que pour le clavier virtuel, afin de pouvoir scroller jusqu'au dernier champ)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindViews()

        // Spinners avec un premier élément "Sélectionner" représentant l'absence de choix (cf. question 4.5)
        spinnerNationality.adapter = createSpinnerAdapter(R.array.nationalities, R.string.nationality_empty)
        spinnerSector.adapter = createSpinnerAdapter(R.array.sectors, R.string.sectors_empty)

        // Affichage des champs spécifiques en fonction de l'occupation choisie
        radioGroupOccupation.setOnCheckedChangeListener { _, _ ->
            radioWorker.error = null
            updateOccupationViews()
        }

        // Le DatePicker s'ouvre aussi bien via le bouton que via le champ de saisie
        btnBirthDate.setOnClickListener { showBirthDatePicker() }
        editBirthDate.setOnClickListener { showBirthDatePicker() }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { clearForm() }
        findViewById<Button>(R.id.btn_ok).setOnClickListener { submitForm() }

        // Après une rotation, le MaterialDatePicker (DialogFragment) est recréé automatiquement par le
        // FragmentManager, mais pas ses listeners : il faut donc les rattacher (cf. question 4.3)
        @Suppress("UNCHECKED_CAST")
        (supportFragmentManager.findFragmentByTag(DATE_PICKER_TAG) as? MaterialDatePicker<Long>)
            ?.let { attachBirthDatePickerListener(it) }

        // Au premier lancement uniquement, on initialise la vue avec un objet du modèle.
        // Lors d'une recréation (rotation...), le contenu des vues est restauré par le système.
        if (savedInstanceState == null) {
            fillForm(Person.exampleWorker)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        birthDay?.let { outState.putLong(STATE_BIRTHDAY, it.timeInMillis) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState.containsKey(STATE_BIRTHDAY)) {
            setBirthDay(Calendar.getInstance().apply {
                timeInMillis = savedInstanceState.getLong(STATE_BIRTHDAY)
            })
        }
        updateOccupationViews()
    }

    private fun bindViews() {
        editName = findViewById(R.id.edit_name)
        editFirstName = findViewById(R.id.edit_firstname)
        editBirthDate = findViewById(R.id.edit_birthdate)
        btnBirthDate = findViewById(R.id.btn_birthdate)
        spinnerNationality = findViewById(R.id.spinner_nationality)
        radioGroupOccupation = findViewById(R.id.radiogroup_occupation)
        radioWorker = findViewById(R.id.radio_worker)

        groupStudent = findViewById(R.id.group_student)
        editSchool = findViewById(R.id.edit_school)
        editGraduationYear = findViewById(R.id.edit_graduationyear)

        groupWorker = findViewById(R.id.group_worker)
        editCompany = findViewById(R.id.edit_company)
        spinnerSector = findViewById(R.id.spinner_sector)
        editExperience = findViewById(R.id.edit_experience)

        editEmail = findViewById(R.id.edit_email)
        editRemark = findViewById(R.id.edit_remark)
    }

    // ------------------------------------------------------------------------------------------
    // Modèle -> Vue
    // ------------------------------------------------------------------------------------------

    /**
     * Initialise tous les champs du formulaire à partir d'une Person existante
     */
    private fun fillForm(person: Person) {
        editName.setText(person.name)
        editFirstName.setText(person.firstName)
        // on travaille sur une copie pour ne pas modifier l'objet du modèle
        setBirthDay(person.birthDay.clone() as Calendar)
        setSpinnerValue(spinnerNationality, person.nationality)
        editEmail.setText(person.email)
        editRemark.setText(person.remark)

        when (person) {
            is Student -> {
                radioGroupOccupation.check(R.id.radio_student)
                editSchool.setText(person.university)
                editGraduationYear.setText(person.graduationYear.toString())
            }
            is Worker -> {
                radioGroupOccupation.check(R.id.radio_worker)
                editCompany.setText(person.company)
                setSpinnerValue(spinnerSector, person.sector)
                editExperience.setText(person.experienceYear.toString())
            }
        }
    }

    /**
     * Vide le contenu de tous les éléments du formulaire
     */
    private fun clearForm() {
        listOf(
            editName, editFirstName, editBirthDate, editSchool, editGraduationYear,
            editCompany, editExperience, editEmail, editRemark
        ).forEach {
            it.text.clear()
            it.error = null
        }
        birthDay = null
        spinnerNationality.setSelection(NO_SELECTION)
        spinnerSector.setSelection(NO_SELECTION)
        radioGroupOccupation.clearCheck() // déclenche updateOccupationViews() via le listener
        radioWorker.error = null
        currentFocus?.clearFocus()
    }

    /**
     * Affiche uniquement la section correspondant à l'occupation choisie (aucune si rien n'est coché)
     * et adapte l'ordre de passage d'un champ à l'autre avec le bouton "suivant" du clavier
     */
    private fun updateOccupationViews() {
        val checkedId = radioGroupOccupation.checkedRadioButtonId
        groupStudent.visibility = if (checkedId == R.id.radio_student) View.VISIBLE else View.GONE
        groupWorker.visibility = if (checkedId == R.id.radio_worker) View.VISIBLE else View.GONE

        // après le prénom, on passe au premier champ spécifique visible (cf. question 4.4)
        editFirstName.nextFocusForwardId = when (checkedId) {
            R.id.radio_student -> R.id.edit_school
            R.id.radio_worker -> R.id.edit_company
            else -> R.id.edit_email
        }
    }

    // ------------------------------------------------------------------------------------------
    // Vue -> Modèle
    // ------------------------------------------------------------------------------------------

    private fun submitForm() {
        val person = createPersonFromForm()
        if (person == null) {
            Toast.makeText(this, R.string.form_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        Log.i(TAG, person.toString())

        val type = getString(
            if (person is Student) R.string.main_base_occupation_student else R.string.main_base_occupation_worker
        )
        Toast.makeText(this, getString(R.string.form_created, type), Toast.LENGTH_SHORT).show()
    }

    /**
     * Crée un Student ou un Worker à partir des données saisies.
     * Les champs obligatoires manquants sont signalés à l'utilisateur et null est retourné.
     */
    private fun createPersonFromForm(): Person? {
        var valid = true

        // Retourne le texte saisi, ou signale une erreur si le champ est vide
        fun EditText.requireText(): String {
            val value = text.toString().trim()
            if (value.isEmpty()) {
                error = getString(R.string.error_required)
                valid = false
            }
            return value
        }

        // Retourne le nombre saisi, ou signale une erreur si le champ est vide ou invalide
        fun EditText.requireInt(): Int {
            val value = text.toString().trim().toIntOrNull()
            if (value == null) {
                error = getString(R.string.error_required)
                valid = false
            }
            return value ?: 0
        }

        // Retourne la valeur choisie, ou signale une erreur si "Sélectionner" est encore choisi
        fun Spinner.requireValue(): String {
            val value = getSpinnerValue(this)
            if (value == null) {
                (selectedView as? TextView)?.error = getString(R.string.error_required)
                valid = false
            }
            return value.orEmpty()
        }

        val name = editName.requireText()
        val firstName = editFirstName.requireText()
        val nationality = spinnerNationality.requireValue()
        val email = editEmail.text.toString().trim()
        val remark = editRemark.text.toString().trim()

        val birthDay = birthDay
        if (birthDay == null) {
            editBirthDate.error = getString(R.string.error_required)
            valid = false
        }

        val person = when (radioGroupOccupation.checkedRadioButtonId) {
            R.id.radio_student -> {
                val school = editSchool.requireText()
                val graduationYear = editGraduationYear.requireInt()
                if (valid) Student(name, firstName, birthDay!!, nationality, school, graduationYear, email, remark)
                else null
            }
            R.id.radio_worker -> {
                val company = editCompany.requireText()
                val sector = spinnerSector.requireValue()
                val experience = editExperience.requireInt()
                if (valid) Worker(name, firstName, birthDay!!, nationality, company, sector, experience, email, remark)
                else null
            }
            else -> {
                radioWorker.error = getString(R.string.error_occupation)
                null
            }
        }
        return person
    }

    // ------------------------------------------------------------------------------------------
    // Date de naissance
    // ------------------------------------------------------------------------------------------

    private fun setBirthDay(date: Calendar?) {
        birthDay = date
        editBirthDate.setText(date?.let { dateFormatter.format(it.time) })
        editBirthDate.error = null
    }

    /**
     * Ouvre un MaterialDatePicker présélectionné sur la date de naissance si elle est définie,
     * sinon sur la date du jour. Seules les dates des 110 dernières années sont sélectionnables.
     */
    private fun showBirthDatePicker() {
        // évite d'ouvrir deux dialogues en cas de double clic
        if (supportFragmentManager.findFragmentByTag(DATE_PICKER_TAG) != null) return

        // Le MaterialDatePicker travaille avec des timestamps à minuit UTC
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        val minDate = Calendar.getInstance(UTC).apply {
            timeInMillis = today
            add(Calendar.YEAR, -MAX_AGE)
        }.timeInMillis
        val selection = birthDay?.let { localDateToUtcMillis(it) } ?: today

        val constraints = CalendarConstraints.Builder()
            .setStart(minDate)
            .setEnd(today)
            .setOpenAt(selection)
            .setValidator(
                CompositeDateValidator.allOf(
                    listOf(DateValidatorPointForward.from(minDate), DateValidatorPointBackward.before(today))
                )
            )
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.main_base_birthdate_dialog_title)
            .setSelection(selection)
            .setCalendarConstraints(constraints)
            .build()

        attachBirthDatePickerListener(picker)
        picker.show(supportFragmentManager, DATE_PICKER_TAG)
    }

    private fun attachBirthDatePickerListener(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { selection ->
            setBirthDay(utcMillisToLocalDate(selection))
        }
    }

    /** Convertit une date locale en timestamp à minuit UTC (format attendu par le MaterialDatePicker) */
    private fun localDateToUtcMillis(date: Calendar): Long =
        Calendar.getInstance(UTC).apply {
            clear()
            set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis

    /** Convertit un timestamp à minuit UTC (renvoyé par le MaterialDatePicker) en date locale */
    private fun utcMillisToLocalDate(millis: Long): Calendar {
        val utc = Calendar.getInstance(UTC).apply { timeInMillis = millis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }
    }

    // ------------------------------------------------------------------------------------------
    // Spinners
    // ------------------------------------------------------------------------------------------

    /**
     * Crée un adapter dont le premier élément est un label (ex. "Sélectionner") représentant
     * l'absence de choix. Cet élément est grisé et ne peut pas être choisi dans la liste déroulante.
     */
    private fun createSpinnerAdapter(@ArrayRes values: Int, @StringRes emptyLabel: Int): ArrayAdapter<String> {
        val items = listOf(getString(emptyLabel)) + resources.getStringArray(values)
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun isEnabled(position: Int) = position != NO_SELECTION

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    alpha = if (position == NO_SELECTION) 0.5f else 1f
                }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    /** Retourne la valeur choisie, ou null si c'est le premier élément ("Sélectionner") */
    private fun getSpinnerValue(spinner: Spinner): String? =
        if (spinner.selectedItemPosition == NO_SELECTION) null
        else spinner.selectedItem as String

    /** Sélectionne la valeur donnée, ou le premier élément ("Sélectionner") si elle est null ou inconnue */
    @Suppress("UNCHECKED_CAST")
    private fun setSpinnerValue(spinner: Spinner, value: String?) {
        val position = (spinner.adapter as ArrayAdapter<String>).getPosition(value)
        spinner.setSelection(if (position > NO_SELECTION) position else NO_SELECTION)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DATE_PICKER_TAG = "birthdate_picker"
        private const val STATE_BIRTHDAY = "birthday"

        // position de l'élément "Sélectionner" dans les spinners
        private const val NO_SELECTION = 0

        // âge maximal accepté pour la date de naissance
        private const val MAX_AGE = 110

        private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}
